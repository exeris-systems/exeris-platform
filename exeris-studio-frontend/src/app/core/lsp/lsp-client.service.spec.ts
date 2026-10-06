import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { DEFAULT_LSP_URL, LspClientService } from './lsp-client.service';
import { SchemaVersionSkewError, SCHEMA_VERSION_SKEW_CODE } from './lsp-domain.types';

class MockWebSocket {
  static instances: MockWebSocket[] = [];
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;

  readyState = MockWebSocket.OPEN;
  sentMessages: string[] = [];

  onopen: (() => void) | null = null;
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onerror: ((error: unknown) => void) | null = null;
  onclose: (() => void) | null = null;

  constructor(public url: string) {
    MockWebSocket.instances.push(this);
    setTimeout(() => {
      if (this.onopen) this.onopen();
    }, 0);
  }

  send(data: string): void {
    this.sentMessages.push(data);
  }

  close(): void {
    this.readyState = 3; // WebSocket.CLOSED
    if (this.onclose) this.onclose();
  }

  simulateServerResponse(response: unknown): void {
    if (this.onmessage) {
      this.onmessage({ data: JSON.stringify(response) } as MessageEvent<string>);
    }
  }
}

const TEST_URL = 'ws://lsp.test:1/lsp';

describe('LspClientService', () => {
  let service: LspClientService;
  let originalWebSocket: typeof WebSocket;

  beforeEach(() => {
    MockWebSocket.instances = [];
    originalWebSocket = globalThis.WebSocket;
    (globalThis as unknown as { WebSocket: unknown }).WebSocket = MockWebSocket;

    TestBed.configureTestingModule({
      providers: [LspClientService],
    });
    service = TestBed.inject(LspClientService);
  });

  afterEach(() => {
    service.disconnect();
    (globalThis as unknown as { WebSocket: unknown }).WebSocket = originalWebSocket;
  });

  it('should default to the launcher WebSocket endpoint (port 5007, path /lsp)', () => {
    // Mirrors LauncherOptions.DEFAULT_PORT and the path LspMain serves.
    expect(DEFAULT_LSP_URL).toBe('ws://127.0.0.1:5007/lsp');
    service.connect();
    expect(MockWebSocket.instances[0].url).toBe('ws://127.0.0.1:5007/lsp');
  });

  it('should connect to WebSocket URL and dispatch initialize request', async () => {
    service.connect(TEST_URL);
    const mockWs = MockWebSocket.instances[0];
    expect(mockWs).toBeDefined();

    const initPromise = new Promise((resolve) => {
      service.initialize('file:///tmp/test').subscribe(resolve);
    });

    await new Promise((r) => setTimeout(r, 10));

    expect(mockWs.sentMessages.length).toBe(1);
    const sent = JSON.parse(mockWs.sentMessages[0]);
    expect(sent.method).toBe('initialize');
    expect(sent.params.rootUri).toBe('file:///tmp/test');

    mockWs.simulateServerResponse({
      jsonrpc: '2.0',
      id: sent.id,
      result: { capabilities: {} },
    });

    const result = await initPromise;
    expect(result).toEqual({ capabilities: {} });
  });

  it('ignores events from a socket it has replaced', async () => {
    service.connect(TEST_URL);
    const first = MockWebSocket.instances[0];
    // A browser delivers close asynchronously, after the replacement socket exists.
    first.close = () => {
      first.readyState = MockWebSocket.CLOSED;
    };
    await new Promise((r) => setTimeout(r, 10));

    service.connect('ws://other.test:2/lsp');
    const second = MockWebSocket.instances[1];
    await new Promise((r) => setTimeout(r, 10));

    let settled: 'resolved' | 'rejected' | null = null;
    service.request('exeris/domains', {}).subscribe({
      next: () => (settled = 'resolved'),
      error: () => (settled = 'rejected'),
    });
    const sent = JSON.parse(second.sentMessages[0]);

    first.onclose?.();
    first.simulateServerResponse({ jsonrpc: '2.0', id: sent.id, result: ['stale'] });
    await new Promise((r) => setTimeout(r, 10));

    expect(settled).toBeNull();
    expect(MockWebSocket.instances.length).toBe(2);
    let state: string | undefined;
    service.connectionState$.subscribe((s) => (state = s)).unsubscribe();
    expect(state).toBe('connected');

    second.simulateServerResponse({ jsonrpc: '2.0', id: sent.id, result: [] });
    expect(settled).toBe('resolved');
  });

  it('should dispatch exeris/domains and return domain list', async () => {
    service.connect(TEST_URL);
    const mockWs = MockWebSocket.instances[0];

    const domainsPromise = new Promise((resolve) => {
      service.getDomains().subscribe(resolve);
    });

    await new Promise((r) => setTimeout(r, 10));

    expect(mockWs.sentMessages.length).toBe(1);
    const sent = JSON.parse(mockWs.sentMessages[0]);
    expect(sent.method).toBe('exeris/domains');

    const sampleDomains = [
      {
        qualifiedName: 'com.example.Order',
        simpleName: 'Order',
        packageName: 'com.example',
        sourcePath: 'com/example/Order.java',
      },
    ];

    mockWs.simulateServerResponse({
      jsonrpc: '2.0',
      id: sent.id,
      result: sampleDomains,
    });

    const result = await domainsPromise;
    expect(result).toEqual(sampleDomains);
  });

  it('should dispatch exeris/domainDescribe and carry relationships', async () => {
    service.connect(TEST_URL);
    const mockWs = MockWebSocket.instances[0];

    const describePromise = new Promise((resolve) => {
      service.getDomainDescribe('com.example.Order').subscribe(resolve);
    });

    await new Promise((r) => setTimeout(r, 10));

    expect(mockWs.sentMessages.length).toBe(1);
    const sent = JSON.parse(mockWs.sentMessages[0]);
    expect(sent.method).toBe('exeris/domainDescribe');
    expect(sent.params.qualifiedName).toBe('com.example.Order');

    const sampleDescription = {
      qualifiedName: 'com.example.Order',
      simpleName: 'Order',
      packageName: 'com.example',
      sourcePath: 'com/example/Order.java',
      fields: [{ name: 'code', type: 'String', required: true }],
      actions: [{ name: 'submit', httpMethod: 'POST', resultType: 'void', params: [] }],
      artefacts: ['rest'],
      relationships: [
        { name: 'items', targetEntity: 'OrderItem', type: 'ONE_TO_MANY' },
      ],
    };

    mockWs.simulateServerResponse({
      jsonrpc: '2.0',
      id: sent.id,
      result: sampleDescription,
    });

    const result = (await describePromise) as typeof sampleDescription;
    expect(result.relationships).toBeDefined();
    expect(result.relationships?.length).toBe(1);
    expect(result.relationships?.[0]).toEqual({ name: 'items', targetEntity: 'OrderItem', type: 'ONE_TO_MANY' });
  });

  it('should map schema version skew error code to SchemaVersionSkewError', async () => {
    service.connect(TEST_URL);
    const mockWs = MockWebSocket.instances[0];

    const errorPromise = new Promise<Error>((resolve) => {
      service.getDomains().subscribe({
        error: resolve,
      });
    });

    await new Promise((r) => setTimeout(r, 10));

    const sent = JSON.parse(mockWs.sentMessages[0]);

    mockWs.simulateServerResponse({
      jsonrpc: '2.0',
      id: sent.id,
      error: {
        code: SCHEMA_VERSION_SKEW_CODE,
        message: 'SCHEMA_VERSION_SKEW: Client and server versions differ',
      },
    });

    const error = await errorPromise;
    expect(error instanceof SchemaVersionSkewError).toBe(true);
    expect(error.message).toContain('SCHEMA_VERSION_SKEW');
  });
});
