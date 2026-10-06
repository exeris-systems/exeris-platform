import { Injectable, NgZone, inject } from '@angular/core';
import { Observable, Subject, BehaviorSubject, throwError, of } from 'rxjs';
import { filter, map, take, timeout, catchError } from 'rxjs/operators';
import {
  DomainSummaryProjection,
  DomainDescriptionProjection,
  ActionSummaryProjection,
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcError,
  SchemaVersionSkewError,
  SCHEMA_VERSION_SKEW_CODE,
} from './lsp-domain.types';

/** The standalone launcher's WebSocket endpoint at its default host and port. */
export const DEFAULT_LSP_URL = 'ws://127.0.0.1:5007/lsp';

export type LspConnectionState = 'connected' | 'connecting' | 'disconnected' | 'error';

@Injectable({
  providedIn: 'root',
})
export class LspClientService {
  private readonly ngZone = inject(NgZone);

  private socket: WebSocket | null = null;
  private correlationId = 0;
  private readonly pendingRequests = new Map<
    number | string,
    {
      resolve: (value: unknown) => void;
      reject: (reason: unknown) => void;
    }
  >();

  private readonly connectionStateSubject = new BehaviorSubject<LspConnectionState>('disconnected');
  public readonly connectionState$ = this.connectionStateSubject.asObservable();

  private activeUrl = DEFAULT_LSP_URL;
  private shouldReconnect = true;
  private isReconnecting = false;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private initializedRootUri: string | null = null;

  public get isConnected(): boolean {
    return this.socket?.readyState === (typeof WebSocket !== 'undefined' ? WebSocket.OPEN : 1);
  }

  public connect(url: string = this.activeUrl): void {
    const OPEN_STATE = typeof WebSocket !== 'undefined' ? WebSocket.OPEN : 1;
    const CONNECTING_STATE = typeof WebSocket !== 'undefined' ? WebSocket.CONNECTING : 0;
    if (this.socket && (this.socket.readyState === OPEN_STATE || this.socket.readyState === CONNECTING_STATE)) {
      if (this.activeUrl === url) return;
      this.disconnect();
    }

    this.activeUrl = url;
    this.shouldReconnect = true;
    this.setConnectionState('connecting');

    try {
      this.socket = new WebSocket(url);

      this.socket.onopen = () => {
        this.ngZone.run(() => {
          this.setConnectionState('connected');
          if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
          }
          if (this.isReconnecting && this.initializedRootUri) {
            this.isReconnecting = false;
            this.initialize(this.initializedRootUri).subscribe({
              error: (err) => console.error('Failed to re-initialize LSP session:', err),
            });
          }
        });
      };

      this.socket.onmessage = (event: MessageEvent<string>) => {
        this.ngZone.run(() => {
          this.handleIncomingMessage(event.data);
        });
      };

      this.socket.onerror = (err) => {
        this.ngZone.run(() => {
          console.warn('LSP WebSocket error:', err);
          this.setConnectionState('error');
        });
      };

      this.socket.onclose = () => {
        this.ngZone.run(() => {
          this.setConnectionState('disconnected');
          this.rejectAllPending('LSP WebSocket closed');
          if (this.shouldReconnect) {
            this.scheduleReconnect();
          }
        });
      };
    } catch (err) {
      this.setConnectionState('error');
      if (this.shouldReconnect) {
        this.scheduleReconnect();
      }
    }
  }

  public disconnect(): void {
    this.shouldReconnect = false;
    this.isReconnecting = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.socket) {
      try {
        this.socket.close(1000, 'Client disconnecting');
      } catch {
        // ignore
      }
      this.socket = null;
    }
    this.setConnectionState('disconnected');
    this.rejectAllPending('Client disconnected');
  }

  public request<TParams = unknown, TResult = unknown>(method: string, params?: TParams): Observable<TResult> {
    const id = ++this.correlationId;
    const req: JsonRpcRequest<TParams> = {
      jsonrpc: '2.0',
      id,
      method,
      params,
    };

    return new Observable<TResult>((observer) => {
      if (!this.isConnected) {
        this.connect();
      }

      const send = () => {
        const OPEN_STATE = typeof WebSocket !== 'undefined' ? WebSocket.OPEN : 1;
        if (!this.socket || this.socket.readyState !== OPEN_STATE) {
          observer.error(new Error(`Cannot send request '${method}': WebSocket not connected.`));
          return;
        }

        this.pendingRequests.set(id, {
          resolve: (val) => {
            observer.next(val as TResult);
            observer.complete();
          },
          reject: (err) => {
            observer.error(err);
          },
        });

        this.socket.send(JSON.stringify(req));
      };

      if (this.isConnected) {
        send();
      } else {
        const sub = this.connectionState$
          .pipe(
            filter((state) => state === 'connected' || state === 'error' || state === 'disconnected'),
            take(1),
            timeout(5000),
            catchError((e) => throwError(() => new Error(`Connection timeout waiting to send '${method}': ${e}`))),
          )
          .subscribe({
            next: (state) => {
              if (state === 'connected') {
                send();
              } else {
                observer.error(new Error(`Failed to connect to LSP server: state is '${state}'`));
              }
            },
            error: (err) => observer.error(err),
          });

        return () => {
          sub.unsubscribe();
          this.pendingRequests.delete(id);
        };
      }

      return () => {
        this.pendingRequests.delete(id);
      };
    });
  }

  public notify(method: string, params?: unknown): void {
    const OPEN_STATE = typeof WebSocket !== 'undefined' ? WebSocket.OPEN : 1;
    if (!this.socket || this.socket.readyState !== OPEN_STATE) return;
    this.socket.send(
      JSON.stringify({
        jsonrpc: '2.0',
        method,
        params,
      }),
    );
  }

  public initialize(rootUri: string): Observable<unknown> {
    this.initializedRootUri = rootUri;
    return this.request('initialize', {
      rootUri,
      capabilities: {},
    });
  }

  public getDomains(): Observable<DomainSummaryProjection[]> {
    return this.request<Record<string, never>, DomainSummaryProjection[]>('exeris/domains', {});
  }

  public getDomainDescribe(qualifiedName: string): Observable<DomainDescriptionProjection> {
    return this.request<{ qualifiedName: string }, DomainDescriptionProjection>('exeris/domainDescribe', {
      qualifiedName,
    });
  }

  public getActions(): Observable<ActionSummaryProjection[]> {
    return this.request<Record<string, never>, ActionSummaryProjection[]>('exeris/actions', {});
  }

  private handleIncomingMessage(raw: string): void {
    let message: JsonRpcResponse;
    try {
      message = JSON.parse(raw);
    } catch (err) {
      console.error('Failed to parse incoming JSON-RPC frame:', raw, err);
      return;
    }

    if (message.id !== undefined && message.id !== null) {
      const pending = this.pendingRequests.get(message.id);
      if (pending) {
        this.pendingRequests.delete(message.id);
        if (message.error) {
          const err = this.mapJsonRpcError(message.error);
          pending.reject(err);
        } else {
          pending.resolve(message.result);
        }
      }
    }
  }

  private mapJsonRpcError(error: JsonRpcError): Error {
    if (
      error.code === SCHEMA_VERSION_SKEW_CODE ||
      error.message.includes('SCHEMA_VERSION_SKEW') ||
      error.message.includes('Schema skew') ||
      error.message.toLowerCase().includes('schema version mismatch')
    ) {
      return new SchemaVersionSkewError(error.message, error.data);
    }
    return new Error(`JSON-RPC Error [${error.code}]: ${error.message}`);
  }

  private rejectAllPending(reason: string): void {
    const error = new Error(reason);
    for (const pending of this.pendingRequests.values()) {
      pending.reject(error);
    }
    this.pendingRequests.clear();
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return;
    this.isReconnecting = true;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (this.shouldReconnect) {
        this.connect(this.activeUrl);
      }
    }, 2000);
  }

  private setConnectionState(state: LspConnectionState): void {
    this.connectionStateSubject.next(state);
  }
}
