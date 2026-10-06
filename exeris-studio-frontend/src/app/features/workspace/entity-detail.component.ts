import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { LspClientService } from '../../core/lsp/lsp-client.service';
import {
  DomainDescriptionProjection,
  DomainSummaryProjection,
  SchemaVersionSkewError,
} from '../../core/lsp/lsp-domain.types';

@Component({
  selector: 'app-entity-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-6">
      <!-- Breadcrumb Navigation -->
      <nav class="flex items-center space-x-2 text-sm text-gray-500 dark:text-gray-400" aria-label="Breadcrumb">
        <a routerLink="/workspaces" class="hover:text-indigo-600 dark:hover:text-indigo-400">Workspaces</a>
        <span>/</span>
        <a [routerLink]="['/workspace', workspacePath()]" class="hover:text-indigo-600 dark:hover:text-indigo-400 truncate max-w-xs">
          {{ workspacePath() }}
        </a>
        <span>/</span>
        <span class="font-medium text-gray-900 dark:text-white truncate">
          {{ entity()?.simpleName ?? entityName() }}
        </span>
      </nav>

      <!-- Back button, connection badge, refresh and title -->
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div class="flex items-center gap-3">
            <span class="inline-flex items-center justify-center h-10 w-10 rounded-lg bg-indigo-100 text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300 font-bold text-base">
              E
            </span>
            <div>
              <h1 class="text-2xl font-bold tracking-tight text-gray-900 dark:text-white flex items-center gap-2">
                {{ entity()?.simpleName ?? entityName() }}
              </h1>
              <p class="text-xs text-gray-500 dark:text-gray-400 font-mono mt-0.5">
                {{ entity()?.qualifiedName }}
              </p>
            </div>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <!-- Connection State Badge -->
          <span
            class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
            [ngClass]="{
              'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300': connectionState() === 'connected',
              'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300': connectionState() === 'connecting',
              'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300': connectionState() === 'error' || connectionState() === 'disconnected'
            }"
          >
            <span
              class="h-1.5 w-1.5 rounded-full mr-1.5"
              [ngClass]="{
                'bg-green-500': connectionState() === 'connected',
                'bg-amber-500': connectionState() === 'connecting',
                'bg-red-500': connectionState() === 'error' || connectionState() === 'disconnected'
              }"
            ></span>
            LSP: {{ connectionState() }}
          </span>

          <button
            type="button"
            (click)="refresh()"
            class="exeris-btn exeris-btn-secondary exeris-btn-sm inline-flex items-center gap-1.5"
            [disabled]="isLoading()"
          >
            <svg class="h-4 w-4" [class.animate-spin]="isLoading()" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh
          </button>

          <a
            [routerLink]="['/workspace', workspacePath()]"
            class="exeris-btn exeris-btn-secondary exeris-btn-sm inline-flex items-center gap-1"
          >
            &larr; Back to Tree
          </a>
        </div>
      </div>

      <!-- Errors -->
      @if (schemaSkewError()) {
        <div class="p-4 rounded-md bg-amber-50 dark:bg-amber-900/20 border border-amber-300 dark:border-amber-700 text-amber-800 dark:text-amber-200 text-sm">
          <strong>Schema Skew Warning:</strong> {{ schemaSkewError() }}
        </div>
      }

      @if (generalError()) {
        <div class="p-4 rounded-md bg-red-50 dark:bg-red-900/20 border border-red-300 dark:border-red-700 text-red-800 dark:text-red-200 text-sm flex items-center justify-between">
          <span>{{ generalError() }}</span>
          <button type="button" (click)="refresh()" class="exeris-btn exeris-btn-secondary exeris-btn-sm ml-4">
            Try again
          </button>
        </div>
      }

      @if (isLoading()) {
        <div class="exeris-card p-12 text-center text-gray-500 dark:text-gray-400">
          <svg class="animate-spin h-8 w-8 mx-auto text-indigo-600 mb-2" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          Loading entity description from LSP...
        </div>
      } @else if (entity()) {
        <!-- Artefacts and Metadata Header Card -->
        <div class="exeris-card p-5 space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <span class="text-xs uppercase font-medium text-gray-400">Package</span>
              <p class="font-mono text-sm text-gray-900 dark:text-gray-100 mt-0.5">{{ entity()!.packageName }}</p>
            </div>
            <div>
              <span class="text-xs uppercase font-medium text-gray-400">Source Path</span>
              <p class="font-mono text-sm text-gray-900 dark:text-gray-100 mt-0.5 truncate">{{ entity()!.sourcePath }}</p>
            </div>
            <div>
              <span class="text-xs uppercase font-medium text-gray-400">Generated Artefacts</span>
              <div class="flex flex-wrap gap-1.5 mt-1">
                @for (art of entity()!.artefacts; track art) {
                  <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-indigo-50 text-indigo-700 dark:bg-indigo-900/50 dark:text-indigo-300 uppercase">
                    {{ art }}
                  </span>
                } @empty {
                  <span class="text-xs text-gray-400 italic">None</span>
                }
              </div>
            </div>
          </div>
        </div>

        <!-- Section: Fields -->
        <div class="space-y-3">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white flex items-center gap-2">
            Fields
            <span class="text-xs font-normal text-gray-400">({{ entity()!.fields.length }})</span>
          </h2>
          <div class="exeris-card overflow-hidden">
            <table class="exeris-table">
              <thead>
                <tr>
                  <th scope="col">Field Name</th>
                  <th scope="col">Type</th>
                  <th scope="col">Required</th>
                </tr>
              </thead>
              <tbody>
                @for (field of entity()!.fields; track field.name) {
                  <tr>
                    <td class="font-medium text-gray-900 dark:text-white">
                      {{ field.name }}
                    </td>
                    <td class="font-mono text-xs text-indigo-600 dark:text-indigo-400">
                      {{ field.type }}
                    </td>
                    <td>
                      @if (field.required) {
                        <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300">
                          Required
                        </span>
                      } @else {
                        <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400">
                          Optional
                        </span>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="3" class="text-center py-6 text-gray-400 text-sm italic">
                      No fields declared on this domain.
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>

        <!-- Section: Actions -->
        <div class="space-y-3">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white flex items-center gap-2">
            Actions
            <span class="text-xs font-normal text-gray-400">({{ entity()!.actions.length }})</span>
          </h2>
          <div class="exeris-card overflow-hidden">
            <table class="exeris-table">
              <thead>
                <tr>
                  <th scope="col">Action Name</th>
                  <th scope="col">HTTP Method</th>
                  <th scope="col">Return Type</th>
                  <th scope="col">Parameters</th>
                </tr>
              </thead>
              <tbody>
                @for (act of entity()!.actions; track act.name) {
                  <tr>
                    <td class="font-medium text-gray-900 dark:text-white">
                      {{ act.name }}
                    </td>
                    <td>
                      <span
                        class="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold uppercase"
                        [ngClass]="{
                          'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300': act.httpMethod === 'GET',
                          'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300': act.httpMethod === 'POST',
                          'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300': act.httpMethod === 'PUT',
                          'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300': act.httpMethod === 'DELETE'
                        }"
                      >
                        {{ act.httpMethod }}
                      </span>
                    </td>
                    <td class="font-mono text-xs text-gray-600 dark:text-gray-300">
                      {{ act.resultType }}
                    </td>
                    <td class="text-xs">
                      @if (act.params.length > 0) {
                        <div class="space-y-1">
                          @for (p of act.params; track p.name) {
                            <div class="font-mono">
                              <span class="text-gray-800 dark:text-gray-200">{{ p.name }}</span>:
                              <span class="text-indigo-600 dark:text-indigo-400">{{ p.type }}</span>
                              @if (p.required) {
                                <span class="text-red-500 font-sans text-xs ml-1">*</span>
                              }
                            </div>
                          }
                        </div>
                      } @else {
                        <span class="text-gray-400 italic">None</span>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="4" class="text-center py-6 text-gray-400 text-sm italic">
                      No actions declared on this domain.
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>

        <!-- Section: Relationships (ADR-025 Amendment) -->
        <div class="space-y-3">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white flex items-center gap-2">
            Relationships
            <span class="text-xs font-normal text-gray-400">({{ entity()!.relationships?.length ?? 0 }})</span>
          </h2>
          <div class="exeris-card overflow-hidden">
            @if (entity()!.relationships === null || entity()!.relationships === undefined) {
              <div class="p-6 text-center text-gray-400 text-sm italic">
                Relationships not carried by pipeline metadata.
              </div>
            } @else if (entity()!.relationships!.length === 0) {
              <div class="p-6 text-center text-gray-400 text-sm italic">
                No relationships declared on this domain.
              </div>
            } @else {
              <table class="exeris-table">
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Target Domain</th>
                    <th scope="col">Type</th>
                  </tr>
                </thead>
                <tbody>
                  @for (rel of entity()!.relationships!; track rel.name) {
                    <tr>
                      <td class="font-medium text-gray-900 dark:text-white">
                        {{ rel.name }}
                      </td>
                      <td class="font-mono text-xs">
                        @if (resolveTarget(rel.targetEntity); as target) {
                          <a
                            (click)="navigateToTarget(target)"
                            [attr.title]="target"
                            class="text-indigo-600 dark:text-indigo-400 hover:underline cursor-pointer"
                          >
                            {{ rel.targetEntity }}
                          </a>
                        } @else {
                          <span class="text-gray-700 dark:text-gray-300">{{ rel.targetEntity }}</span>
                        }
                      </td>
                      <td>
                        <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300">
                          {{ rel.type ?? '—' }}
                        </span>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class EntityDetailComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly lspClient = inject(LspClientService);

  readonly workspacePath = signal<string>('');
  readonly entityName = signal<string>('');
  readonly entity = signal<DomainDescriptionProjection | null>(null);
  readonly domains = signal<DomainSummaryProjection[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly generalError = signal<string | null>(null);
  readonly schemaSkewError = signal<string | null>(null);
  readonly connectionState = signal<string>('disconnected');

  private connSub?: Subscription;

  ngOnInit(): void {
    this.connSub = this.lspClient.connectionState$.subscribe((state) => {
      this.connectionState.set(state);
    });

    this.route.paramMap.subscribe((params) => {
      const path = params.get('path');
      const name = params.get('name');

      if (path && name) {
        this.workspacePath.set(path);
        this.entityName.set(name);
        this.loadEntity(path, name);
      }
    });
  }

  ngOnDestroy(): void {
    this.connSub?.unsubscribe();
  }

  refresh(): void {
    const path = this.workspacePath();
    const name = this.entityName();
    if (path && name) {
      this.loadEntity(path, name);
    }
  }

  /**
   * Resolves a relationship's declared target against the workspace's domains. The LSP carries the
   * name as declared in source, so a dotted name matches a qualifiedName and a bare name matches a
   * simpleName. Only a single match is a domain identity; none or several resolve to null.
   */
  resolveTarget(targetEntity: string): string | null {
    const matches = this.domains().filter((d) =>
      targetEntity.includes('.') ? d.qualifiedName === targetEntity : d.simpleName === targetEntity,
    );
    return matches.length === 1 ? matches[0].qualifiedName : null;
  }

  navigateToTarget(targetQualifiedName: string): void {
    void this.router.navigate(['/workspace', this.workspacePath(), 'entity', targetQualifiedName]);
  }

  private loadEntity(path: string, qualifiedName: string): void {
    this.isLoading.set(true);
    this.generalError.set(null);
    this.schemaSkewError.set(null);

    const rootUri = path.startsWith('file:') ? path : `file://${path}`;

    this.lspClient.initialize(rootUri).subscribe({
      next: () => {
        this.lspClient.getDomainDescribe(qualifiedName).subscribe({
          next: (desc) => {
            this.entity.set(desc);
            this.isLoading.set(false);
            this.loadDomains();
          },
          error: (err) => {
            this.isLoading.set(false);
            this.handleError(err);
          },
        });
      },
      error: (err) => {
        this.isLoading.set(false);
        this.handleError(err);
      },
    });
  }

  /** Relationship targets resolve against this list; without it they render as plain text. */
  private loadDomains(): void {
    this.lspClient.getDomains().subscribe({
      next: (domains) => this.domains.set(domains),
      error: () => this.domains.set([]),
    });
  }

  private handleError(err: unknown): void {
    if (err instanceof SchemaVersionSkewError) {
      this.schemaSkewError.set(err.message);
    } else {
      const msg = err instanceof Error ? err.message : String(err);
      this.generalError.set(msg);
    }
  }
}
