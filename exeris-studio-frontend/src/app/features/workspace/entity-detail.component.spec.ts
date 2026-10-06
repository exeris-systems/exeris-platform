import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { EntityDetailComponent } from './entity-detail.component';
import { LspClientService } from '../../core/lsp/lsp-client.service';
import {
  DomainDescriptionProjection,
  DomainSummaryProjection,
  SchemaVersionSkewError,
} from '../../core/lsp/lsp-domain.types';

describe('EntityDetailComponent', () => {
  let component: EntityDetailComponent;
  let fixture: ComponentFixture<EntityDetailComponent>;
  let mockLspClient: {
    connectionState$: Observable<any>;
    initialize: ReturnType<typeof vi.fn>;
    getDomainDescribe: ReturnType<typeof vi.fn>;
    getDomains: ReturnType<typeof vi.fn>;
  };

  const workspaceDomains: DomainSummaryProjection[] = [
    { qualifiedName: 'com.example.shop.Order', simpleName: 'Order', packageName: 'com.example.shop', sourcePath: 'com/example/shop/Order.java' },
    { qualifiedName: 'com.example.shop.OrderItem', simpleName: 'OrderItem', packageName: 'com.example.shop', sourcePath: 'com/example/shop/OrderItem.java' },
  ];

  const sampleDescription: DomainDescriptionProjection = {
    qualifiedName: 'com.example.shop.Order',
    simpleName: 'Order',
    packageName: 'com.example.shop',
    sourcePath: 'com/example/shop/Order.java',
    artefacts: ['rest', 'graphql'],
    fields: [
      { name: 'code', type: 'String', required: true },
      { name: 'total', type: 'double', required: false },
    ],
    actions: [
      {
        name: 'submit',
        httpMethod: 'POST',
        resultType: 'void',
        params: [{ name: 'reason', type: 'String', required: true }],
      },
    ],
    relationships: [
      { name: 'items', targetEntity: 'OrderItem', type: 'ONE_TO_MANY' },
    ],
  };

  beforeEach(async () => {
    mockLspClient = {
      connectionState$: of('connected'),
      initialize: vi.fn().mockReturnValue(of({})),
      getDomainDescribe: vi.fn().mockReturnValue(of(sampleDescription)),
      getDomains: vi.fn().mockReturnValue(of(workspaceDomains)),
    };

    await TestBed.configureTestingModule({
      imports: [EntityDetailComponent],
      providers: [
        provideRouter([]),
        { provide: LspClientService, useValue: mockLspClient },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EntityDetailComponent);
    component = fixture.componentInstance;
    component.workspacePath.set('/tmp/shop');
    component.entityName.set('com.example.shop.Order');
  });

  it('should render entity metadata, fields, actions, and relationships (ADR-025)', () => {
    component['loadEntity']('/tmp/shop', 'com.example.shop.Order');
    fixture.detectChanges();

    const desc = component.entity();
    expect(desc).toBeDefined();
    expect(desc?.simpleName).toBe('Order');
    expect(desc?.packageName).toBe('com.example.shop');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Order');
    expect(compiled.textContent).toContain('com.example.shop');

    // Fields
    expect(desc?.fields.length).toBe(2);
    expect(compiled.textContent).toContain('code');
    expect(compiled.textContent).toContain('total');

    // Actions
    expect(desc?.actions.length).toBe(1);
    expect(compiled.textContent).toContain('submit');
    expect(compiled.textContent).toContain('POST');
    expect(compiled.textContent).toContain('reason');

    // Relationships: omitted = not carried, [] = none
    expect(desc?.relationships).toBeDefined();
    expect(desc?.relationships?.length).toBe(1);
    expect(desc?.relationships?.[0].targetEntity).toBe('OrderItem');
    expect(compiled.textContent).toContain('items');
    expect(compiled.textContent).toContain('OrderItem');
    expect(compiled.textContent).toContain('ONE_TO_MANY');

    // A simple name with exactly one match in exeris/domains resolves to a link.
    const link = compiled.querySelector('a[title="com.example.shop.OrderItem"]');
    expect(link?.textContent?.trim()).toBe('OrderItem');
  });

  it('should render a relationship without a carried type as an em dash', () => {
    mockLspClient.getDomainDescribe = vi.fn().mockReturnValue(
      of({ ...sampleDescription, relationships: [{ name: 'items', targetEntity: 'OrderItem' }] }),
    );

    component['loadEntity']('/tmp/shop', 'com.example.shop.Order');
    fixture.detectChanges();

    const cells = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('td'));
    expect(cells.some((td) => td.textContent?.trim() === '—')).toBe(true);
  });

  it('should resolve a dotted target against qualifiedName only', () => {
    component.domains.set(workspaceDomains);

    expect(component.resolveTarget('com.example.shop.OrderItem')).toBe('com.example.shop.OrderItem');
    expect(component.resolveTarget('com.other.OrderItem')).toBeNull();
  });

  it('should not link an unresolved or ambiguous relationship target', () => {
    mockLspClient.getDomains = vi.fn().mockReturnValue(
      of([
        ...workspaceDomains,
        { qualifiedName: 'com.example.legacy.OrderItem', simpleName: 'OrderItem', packageName: 'com.example.legacy', sourcePath: 'com/example/legacy/OrderItem.java' },
      ]),
    );
    mockLspClient.getDomainDescribe = vi.fn().mockReturnValue(
      of({
        ...sampleDescription,
        relationships: [
          { name: 'items', targetEntity: 'OrderItem', type: 'ONE_TO_MANY' },
          { name: 'customer', targetEntity: 'Customer', type: 'MANY_TO_ONE' },
        ],
      }),
    );

    component['loadEntity']('/tmp/shop', 'com.example.shop.Order');
    fixture.detectChanges();

    expect(component.resolveTarget('OrderItem')).toBeNull();
    expect(component.resolveTarget('Customer')).toBeNull();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('OrderItem');
    expect(compiled.textContent).toContain('Customer');
    const relationshipLinks = Array.from(compiled.querySelectorAll('a')).filter((a) =>
      ['OrderItem', 'Customer'].includes(a.textContent?.trim() ?? ''),
    );
    expect(relationshipLinks).toHaveLength(0);
  });

  it('should handle missing/null relationships gracefully ("not carried")', () => {
    const withoutRelationships: DomainDescriptionProjection = {
      ...sampleDescription,
      relationships: null,
    };
    mockLspClient.getDomainDescribe = vi.fn().mockReturnValue(of(withoutRelationships));

    component['loadEntity']('/tmp/shop', 'com.example.shop.Order');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Relationships not carried by pipeline metadata');
  });

  it('should surface schema version skew errors', () => {
    mockLspClient.getDomainDescribe = vi
      .fn()
      .mockReturnValue(throwError(() => new SchemaVersionSkewError('Schema skew detected')));

    component['loadEntity']('/tmp/shop', 'com.example.shop.Order');
    fixture.detectChanges();

    expect(component.schemaSkewError()).toBe('Schema skew detected');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Schema Skew Warning');
  });

  it('should display "No relationships declared" when relationships is an empty array', () => {
    const emptyRelationships: DomainDescriptionProjection = {
      ...sampleDescription,
      relationships: [],
    };
    mockLspClient.getDomainDescribe = vi.fn().mockReturnValue(of(emptyRelationships));

    component['loadEntity']('/tmp/shop', 'com.example.shop.Order');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No relationships declared on this domain');
  });

  it('should navigate to target entity with clean unencoded path array', () => {
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.workspacePath.set('/tmp/shop');
    component.navigateToTarget('com.example.shop.OrderItem');

    expect(navSpy).toHaveBeenCalledWith([
      '/workspace',
      '/tmp/shop',
      'entity',
      'com.example.shop.OrderItem',
    ]);
  });

  it('should reload entity when refresh is called', () => {
    component.workspacePath.set('/tmp/shop');
    component.entityName.set('com.example.shop.Order');
    component.refresh();

    expect(mockLspClient.initialize).toHaveBeenCalled();
    expect(mockLspClient.getDomainDescribe).toHaveBeenCalledWith('com.example.shop.Order');
  });
});
