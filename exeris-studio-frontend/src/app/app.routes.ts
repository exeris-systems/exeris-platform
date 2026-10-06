import { Routes } from '@angular/router';

/**
 * The workspace routes keep the paths the generated components navigate by: the list links each
 * row relative to itself (`[item.id]`, `[item.id, 'edit']`), and the form and detail navigate to
 * `/workspaces/...`. The list therefore lives at `workspaces`, not at the root.
 */
export const routes: Routes = [
  {
    path: '',
    redirectTo: 'workspaces',
    pathMatch: 'full',
  },
  {
    path: 'workspaces',
    loadComponent: () =>
      import('./generated/components/workspace-list.component').then(
        (m) => m.WorkspaceListComponent,
      ),
  },
  {
    path: 'workspaces/new',
    loadComponent: () =>
      import('./generated/components/workspace-form.component').then(
        (m) => m.WorkspaceFormComponent,
      ),
  },
  {
    path: 'workspaces/:id',
    loadComponent: () =>
      import('./features/workspace/workspace-page.component').then(
        (m) => m.WorkspacePageComponent,
      ),
  },
  {
    path: 'workspaces/:id/edit',
    loadComponent: () =>
      import('./generated/components/workspace-form.component').then(
        (m) => m.WorkspaceFormComponent,
      ),
  },
  {
    path: 'workspace/:path',
    loadComponent: () =>
      import('./features/workspace/workspace-tree.component').then(
        (m) => m.WorkspaceTreeComponent,
      ),
  },
  {
    path: 'workspace/:path/entity/:name',
    loadComponent: () =>
      import('./features/workspace/entity-detail.component').then(
        (m) => m.EntityDetailComponent,
      ),
  },
  {
    path: '**',
    redirectTo: 'workspaces',
  },
];
