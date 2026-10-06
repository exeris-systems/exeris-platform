import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { WorkspaceTreeComponent } from './workspace-tree.component';
import { LspClientService } from '../../core/lsp/lsp-client.service';
import { DomainSummaryProjection, SchemaVersionSkewError } from '../../core/lsp/lsp-domain.types';

describe('WorkspaceTreeComponent', () => {
  let component: WorkspaceTreeComponent;
  let fixture: ComponentFixture<WorkspaceTreeComponent>;
  let mockLspClient: {
    connectionState$: Observable<any>;
    initialize: ReturnType<typeof vi.fn>;
    getDomains: ReturnType<typeof vi.fn>;
  };

  const sampleDomains: DomainSummaryProjection[] = [
    {
      qualifiedName: 'com.example.shop.Order',
      simpleName: 'Order',
      packageName: 'com.example.shop',
      sourcePath: 'com/example/shop/Order.java',
    },
    {
      qualifiedName: 'com.example.shop.Customer',
      simpleName: 'Customer',
      packageName: 'com.example.shop',
      sourcePath: 'com/example/shop/Customer.java',
    },
    {
      qualifiedName: 'com.example.inventory.Warehouse',
      simpleName: 'Warehouse',
      packageName: 'com.example.inventory',
      sourcePath: 'com/example/inventory/Warehouse.java',
    },
  ];

  beforeEach(async () => {
    mockLspClient = {
      connectionState$: of('connected'),
      initialize: vi.fn().mockReturnValue(of({})),
      getDomains: vi.fn().mockReturnValue(of(sampleDomains)),
    };

    await TestBed.configureTestingModule({
      imports: [WorkspaceTreeComponent],
      providers: [
        provideRouter([]),
        { provide: LspClientService, useValue: mockLspClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkspaceTreeComponent);
    component = fixture.componentInstance;
    component.workspacePath.set('/tmp/workspace');
  });

  it('should create and group domains by package', () => {
    fixture.detectChanges();
    component['fetchDomains']();
    fixture.detectChanges();

    const nodes = component.packageNodes();
    expect(nodes.length).toBe(2);

    const inventoryPkg = nodes.find((n) => n.packageName === 'com.example.inventory');
    const shopPkg = nodes.find((n) => n.packageName === 'com.example.shop');

    expect(inventoryPkg).toBeDefined();
    expect(inventoryPkg?.domains.length).toBe(1);
    expect(shopPkg).toBeDefined();
    expect(shopPkg?.domains.length).toBe(2);
  });

  it('should filter domains based on search query', () => {
    component.domains.set(sampleDomains);
    component.searchQuery.set('Order');
    fixture.detectChanges();

    const filtered = component.filteredDomains();
    expect(filtered.length).toBe(1);
    expect(filtered[0].simpleName).toBe('Order');

    const nodes = component.packageNodes();
    expect(nodes.length).toBe(1);
    expect(nodes[0].packageName).toBe('com.example.shop');
  });

  it('should display schema version skew warning when encountered', () => {
    mockLspClient.initialize = vi
      .fn()
      .mockReturnValue(throwError(() => new SchemaVersionSkewError('Version mismatch: server 0.12 vs client 0.11')));

    component['loadWorkspace']('/tmp/workspace');
    fixture.detectChanges();

    expect(component.schemaSkewError()).toContain('Version mismatch');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Schema Version Skew Detected');
  });

  it('should navigate to entity detail with clean unencoded path array', () => {
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.workspacePath.set('/tmp/shop');
    component.openEntity('com.example.shop.Order');

    expect(navSpy).toHaveBeenCalledWith([
      '/workspace',
      '/tmp/shop',
      'entity',
      'com.example.shop.Order',
    ]);
  });

  it('should re-fetch workspace domains when refresh is called', () => {
    fixture.detectChanges();
    component.refresh();
    expect(mockLspClient.initialize).toHaveBeenCalled();
    expect(mockLspClient.getDomains).toHaveBeenCalled();
  });
});
