import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { WorkspacePageComponent } from './workspace-page.component';
import { WorkspaceService } from '../../generated/services/workspace.service';
import type { Workspace } from '../../generated/types/workspace.types';

describe('WorkspacePageComponent', () => {
  let fixture: ComponentFixture<WorkspacePageComponent>;
  let findById: ReturnType<typeof vi.fn>;

  const workspace: Workspace = {
    id: '6f1c2a4e-0b7d-4c8e-9a51-2d3f4b5c6d7e',
    name: 'Shop',
    rootPath: '/home/dev/shop',
  };

  beforeEach(async () => {
    findById = vi.fn().mockReturnValue(of(workspace));

    await TestBed.configureTestingModule({
      imports: [WorkspacePageComponent],
      providers: [provideRouter([]), { provide: WorkspaceService, useValue: { findById } }],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkspacePageComponent);
    fixture.componentRef.setInput('id', workspace.id);
  });

  it('composes the generated detail view for the routed workspace', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('app-workspace-detail')).toBeTruthy();
    expect(findById).toHaveBeenCalledWith(workspace.id);
    expect(element.textContent).toContain('Shop');
  });

  it('links to the workspace tree at the record root path', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const explore = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
      '[data-testid="action-explore"]',
    );
    expect(explore).toBeTruthy();
    expect(explore!.getAttribute('href')).toBe('/workspace/%2Fhome%2Fdev%2Fshop');
  });

  it('offers no exploration before the workspace has loaded', () => {
    findById.mockReturnValue(of());
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="action-explore"]'),
    ).toBeNull();
  });
});
