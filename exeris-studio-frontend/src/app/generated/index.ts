// Generated barrel export
// DO NOT EDIT - This file is auto-generated

// Enums
export * from './types/enums';

// Types (main type definitions)
export * from './types/workspace.types';

// Schemas (Zod validation schemas only)
export * from './schemas/workspace.schema';

// Services (export service classes and pagination types)
export { WorkspaceService, WorkspaceFilter, Page, PageRequest } from './services/workspace.service';

// Stores (signal state over the services)
export { WorkspaceStore } from './stores/workspace.store';
export type { WorkspaceStoreState } from './stores/workspace.store';

// Components
export { WorkspaceFormComponent } from './components/workspace-form.component';
export { WorkspaceListComponent } from './components/workspace-list.component';
export { WorkspaceDetailComponent } from './components/workspace-detail.component';
