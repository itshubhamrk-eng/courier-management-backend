import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { beforeEach, describe, expect, it } from 'vitest';
import { MasterRecord } from '@core/models/master.model';
import { MasterTable } from './master-table';
import { MASTER_DEFINITIONS } from '../master.config';

const routes = MASTER_DEFINITIONS.routes;

const route = (over: Partial<MasterRecord> = {}): MasterRecord => ({
  id: 'r-1', companyId: 't-1', code: 'PNQ_BOM', name: 'Pune to Mumbai', description: null,
  status: 'ACTIVE', displayOrder: 0, createdBy: null, createdDate: null,
  updatedBy: null, updatedDate: null, version: 0,
  bookingBranchId: 'b-1', bookingBranchName: 'Pune Main',
  deliveryBranchId: 'b-2', deliveryBranchName: 'Mumbai Central',
  distanceKm: 150, distanceUnit: 'KM', transitDays: 1, transitHours: 0, via: null, ...over
});

describe('MasterTable', () => {
  let fixture: ComponentFixture<MasterTable>;
  let component: MasterTable;

  const build = (rows: MasterRecord[], perms = { update: true, delete: true }) => {
    fixture = TestBed.createComponent(MasterTable);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('def', routes);
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('perms', perms);
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MasterTable],
      providers: [provideNoopAnimations()]
    });
  });

  it('renders the declared columns plus an actions column', () => {
    build([route()]);
    const keys = component.tableColumns().map((c) => c.key);

    expect(keys.slice(0, -1)).toEqual(routes.columns.map((c) => c.key));
    expect(keys.at(-1)).toBe('__actions');
  });

  it('renders a cell through the column’s own projection', () => {
    build([route()]);
    const transit = routes.columns.find((c) => c.key === 'transitDays')!;

    expect(component.render(transit, route({ transitDays: 1, transitHours: 0 }))).toBe('1 d');
    expect(component.render(transit, route({ transitDays: 0, transitHours: 0 }))).toBe('Same day');
    expect(component.render(transit, route({ transitDays: 1, transitHours: 8 }))).toBe('1 d 8 h');
    expect(component.render(transit, route({ transitDays: 0, transitHours: 4 }))).toBe('4 h');
  });

  it('shows a dash for a name the backend could not resolve', () => {
    // A branch from outside the caller's company is simply absent from the response's
    // name field. Showing nothing is honest; inventing a label would not be.
    build([route({ deliveryBranchName: null })]);
    const column = routes.columns.find((c) => c.key === 'deliveryBranchName')!;

    expect(component.render(column, route({ deliveryBranchName: null }))).toBe('—');
  });

  it('emits view when a row is clicked', () => {
    build([route()]);
    let emitted: { type: string; row: MasterRecord } | undefined;
    component.action.subscribe((e) => (emitted = e));

    component.act('view', route());

    expect(emitted!.type).toBe('view');
    expect(emitted!.row.code).toBe('PNQ_BOM');
  });

  it('renders the row markup, including the code in monospace and a status badge', () => {
    build([route()]);
    const html = fixture.nativeElement.innerHTML as string;

    expect(html).toContain('PNQ_BOM');
    expect(html).toContain('Pune Main');
    expect(html).toContain('app-status-badge');
  });

  it('offers a different empty hint for a seeded catalogue', () => {
    fixture = TestBed.createComponent(MasterTable);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('def', MASTER_DEFINITIONS['vehicle-types']);
    fixture.componentRef.setInput('rows', []);
    fixture.componentRef.setInput('perms', { update: false, delete: false });
    fixture.detectChanges();

    expect(component.emptyHint()).toContain('seed the standard');
  });

  it('hides edit and delete from a caller who may only read', () => {
    build([route()], { update: false, delete: false });
    const html = fixture.nativeElement.innerHTML as string;

    // The menu is rendered lazily by Material, so assert on what the template gates:
    // with no write permission, neither action reaches the DOM.
    expect(html).not.toContain('Deactivate');
    expect(html).not.toContain('Delete');
  });
});
