import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterModule } from '@angular/router';
import { WorkspaceDetailComponent } from '../../generated/components/workspace-detail.component';
import { WorkspaceService } from '../../generated/services/workspace.service';

/**
 * One workspace record, and the way into the tree it points at.
 *
 * The record view is the generated {@link WorkspaceDetailComponent}, composed unchanged. What Studio
 * adds is the step the generated surface cannot know about: from the record to its source tree at
 * `/workspace/:path`, where the domains are read over LSP. The workspace is read through the
 * generated service on demand and held only for the lifetime of this page; nothing about it is
 * stored here.
 */
@Component({
  selector: 'app-workspace-page',
  standalone: true,
  imports: [RouterModule, WorkspaceDetailComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-6">
      @if (rootPath(); as path) {
        <div class="max-w-4xl mx-auto flex justify-end">
          <a
            [routerLink]="['/workspace', path]"
            data-testid="action-explore"
            class="exeris-btn exeris-btn-primary"
          >Explore domains</a>
        </div>
      }
      <app-workspace-detail [id]="id()" />
    </div>
  `,
})
export class WorkspacePageComponent {
  private readonly service = inject(WorkspaceService);

  /** The workspace id, bound from the `:id` route parameter. */
  readonly id = input.required<string>();

  private readonly workspace = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.service.findById(params),
  });

  /** The tree root to explore; absent until the workspace has loaded. */
  readonly rootPath = computed(() =>
    this.workspace.hasValue() ? (this.workspace.value()?.rootPath ?? null) : null,
  );
}
