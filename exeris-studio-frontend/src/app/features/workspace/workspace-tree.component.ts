import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { LspClientService } from '../../core/lsp/lsp-client.service';
import { DomainSummaryProjection, SchemaVersionSkewError } from '../../core/lsp/lsp-domain.types';

export interface PackageNode {
  packageName: string;
  domains: DomainSummaryProjection[];
  isExpanded: boolean;
}

@Component({
  selector: 'app-workspace-tree',
  standalone: true,
  imports: [CommonModule, RouterModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-6">
      <!-- Breadcrumb & Workspace Header -->
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <nav class="flex items-center space-x-2 text-sm text-gray-500 dark:text-gray-400 mb-1" aria-label="Breadcrumb">
            <a routerLink="/workspaces" class="hover:text-indigo-600 dark:hover:text-indigo-400">Workspaces</a>
            <span>/</span>
            <span class="font-medium text-gray-900 dark:text-white truncate max-w-md">{{ workspacePath() }}</span>
          </nav>
          <h1 class="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">Workspace Domains</h1>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Live AST projection of user domains queried over LSP WebSocket.
          </p>
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
        </div>
      </div>

      <!-- Schema Skew Error Warning -->
      @if (schemaSkewError()) {
        <div class="p-4 rounded-md bg-amber-50 dark:bg-amber-900/20 border border-amber-300 dark:border-amber-700 text-amber-800 dark:text-amber-200">
          <div class="flex items-start">
            <svg class="h-5 w-5 text-amber-500 mr-2 shrink-0 mt-0.5" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z" clip-rule="evenodd" />
            </svg>
            <div>
              <h3 class="font-semibold text-sm">Schema Version Skew Detected</h3>
              <p class="text-xs mt-1">{{ schemaSkewError() }}</p>
            </div>
          </div>
        </div>
      }

      <!-- General Error -->
      @if (generalError()) {
        <div class="p-4 rounded-md bg-red-50 dark:bg-red-900/20 border border-red-300 dark:border-red-700 text-red-800 dark:text-red-200 text-sm">
          {{ generalError() }}
        </div>
      }

      <!-- Search Box -->
      <div class="relative">
        <input
          #searchBox
          type="search"
          (input)="searchQuery.set(searchBox.value)"
          placeholder="Filter domains by name or package..."
          class="exeris-input pl-10"
        />
        <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
          <svg class="h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </div>
      </div>

      <!-- Domain Tree / List -->
      <div class="exeris-card overflow-hidden">
        @if (isLoading()) {
          <div class="p-8 text-center text-gray-500 dark:text-gray-400">
            <svg class="animate-spin h-8 w-8 mx-auto text-indigo-600 mb-2" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            Loading domains from LSP server...
          </div>
        } @else if (packageNodes().length === 0) {
          <div class="p-12 text-center text-gray-500 dark:text-gray-400">
            <svg class="mx-auto h-12 w-12 text-gray-400 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
            </svg>
            <p class="font-medium text-gray-900 dark:text-white">No domains found</p>
            <p class="text-sm mt-1">This workspace contains no @ExerisDomain entities or none match your filter.</p>
          </div>
        } @else {
          <div class="divide-y divide-gray-200 dark:divide-gray-800">
            @for (pkg of packageNodes(); track pkg.packageName) {
              <div class="p-4">
                <div
                  (click)="togglePackage(pkg.packageName)"
                  class="flex items-center justify-between cursor-pointer select-none font-mono text-sm font-semibold text-gray-700 dark:text-gray-300 hover:text-indigo-600 dark:hover:text-indigo-400"
                >
                  <div class="flex items-center gap-2">
                    <svg
                      class="h-4 w-4 transition-transform text-gray-400"
                      [class.rotate-90]="pkg.isExpanded"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                    </svg>
                    <span>{{ pkg.packageName }}</span>
                    <span class="ml-2 text-xs font-normal text-gray-400">({{ pkg.domains.length }})</span>
                  </div>
                </div>

                @if (pkg.isExpanded) {
                  <div class="mt-3 ml-6 space-y-2">
                    @for (domain of pkg.domains; track domain.qualifiedName) {
                      <div
                        (click)="openEntity(domain.qualifiedName)"
                        class="flex items-center justify-between p-2.5 rounded-lg border border-transparent hover:border-gray-200 dark:hover:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800/50 cursor-pointer transition-colors"
                      >
                        <div class="flex items-center gap-3">
                          <span class="inline-flex items-center justify-center h-8 w-8 rounded-md bg-indigo-50 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300 font-bold text-xs">
                            E
                          </span>
                          <div>
                            <div class="text-sm font-semibold text-gray-900 dark:text-white">
                              {{ domain.simpleName }}
                            </div>
                            <div class="text-xs text-gray-500 dark:text-gray-400 font-mono">
                              {{ domain.sourcePath }}
                            </div>
                          </div>
                        </div>

                        <span class="text-xs text-indigo-600 dark:text-indigo-400 font-medium hover:underline">
                          Inspect &rarr;
                        </span>
                      </div>
                    }
                  </div>
                }
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
})
export class WorkspaceTreeComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly lspClient = inject(LspClientService);

  readonly workspacePath = signal<string>('');
  readonly domains = signal<DomainSummaryProjection[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly searchQuery = signal<string>('');
  readonly generalError = signal<string | null>(null);
  readonly schemaSkewError = signal<string | null>(null);
  readonly connectionState = signal<string>('disconnected');

  private readonly expandedPackages = signal<Set<string>>(new Set<string>());
  private connSub?: Subscription;

  readonly filteredDomains = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    const all = this.domains();
    if (!q) return all;
    return all.filter(
      (d) =>
        d.simpleName.toLowerCase().includes(q) ||
        d.qualifiedName.toLowerCase().includes(q) ||
        d.packageName.toLowerCase().includes(q),
    );
  });

  readonly packageNodes = computed<PackageNode[]>(() => {
    const map = new Map<string, DomainSummaryProjection[]>();
    for (const d of this.filteredDomains()) {
      const list = map.get(d.packageName) ?? [];
      list.push(d);
      map.set(d.packageName, list);
    }

    const expanded = this.expandedPackages();
    const result: PackageNode[] = [];
    for (const [pkg, items] of map.entries()) {
      result.push({
        packageName: pkg,
        domains: items,
        isExpanded: expanded.has(pkg),
      });
    }

    result.sort((a, b) => a.packageName.localeCompare(b.packageName));
    return result;
  });

  ngOnInit(): void {
    this.connSub = this.lspClient.connectionState$.subscribe((state) => {
      this.connectionState.set(state);
    });

    this.route.paramMap.subscribe((params) => {
      const path = params.get('path');
      if (path) {
        this.workspacePath.set(path);
        this.loadWorkspace(path);
      }
    });
  }

  ngOnDestroy(): void {
    this.connSub?.unsubscribe();
  }

  refresh(): void {
    const path = this.workspacePath();
    if (path) {
      this.loadWorkspace(path);
    }
  }

  togglePackage(pkgName: string): void {
    this.expandedPackages.update((set) => {
      const next = new Set(set);
      if (next.has(pkgName)) {
        next.delete(pkgName);
      } else {
        next.add(pkgName);
      }
      return next;
    });
  }

  openEntity(qualifiedName: string): void {
    void this.router.navigate(['/workspace', this.workspacePath(), 'entity', qualifiedName]);
  }

  private loadWorkspace(path: string): void {
    this.isLoading.set(true);
    this.generalError.set(null);
    this.schemaSkewError.set(null);

    const rootUri = path.startsWith('file:') ? path : `file://${path}`;

    this.lspClient.initialize(rootUri).subscribe({
      next: () => {
        this.fetchDomains();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.handleError(err);
      },
    });
  }

  private fetchDomains(): void {
    this.lspClient.getDomains().subscribe({
      next: (list) => {
        this.domains.set(list);
        this.isLoading.set(false);
        // Expand all packages by default
        const pkgs = new Set<string>(list.map((d) => d.packageName));
        this.expandedPackages.set(pkgs);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.handleError(err);
      },
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
