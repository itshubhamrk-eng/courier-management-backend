import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { beforeEach, describe, expect, it } from 'vitest';
import { MasterFilter } from './master-filter';
import { MASTER_DEFINITIONS } from '../master.config';

describe('MasterFilter', () => {
  let fixture: ComponentFixture<MasterFilter>;
  let component: MasterFilter;
  let http: HttpTestingController;

  const build = (def = MASTER_DEFINITIONS.pincodes) => {
    fixture = TestBed.createComponent(MasterFilter);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('def', def);
    fixture.detectChanges();
  };

  const flushLookups = () => {
    const empty = {
      content: [], page: 0, size: 20, totalElements: 0,
      totalPages: 0, first: true, last: true, hasNext: false
    };
    http.match(() => true).forEach((r) => r.flush({ success: true, data: empty }));
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MasterFilter],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('offers status on every list, plus the list’s own filters', () => {
    build();
    flushLookups();

    expect(component.form.get('status')).toBeTruthy();
    expect(component.form.get('areaId')).toBeTruthy();
    expect(component.form.get('serviceable')).toBeTruthy();
    expect(component.form.get('zone')).toBeTruthy();
  });

  it('emits nothing for the filters left untouched', () => {
    // An unset filter must not become `?areaId=` — the backend would read that as a value.
    build();
    flushLookups();

    let emitted: Record<string, unknown> | undefined;
    component.changed.subscribe((f) => (emitted = f));
    component.apply();

    expect(emitted).toEqual({});
  });

  it('converts a three-state boolean filter to a real boolean', () => {
    // The control holds 'true'/'false' strings because a select cannot hold undefined;
    // the query needs an actual boolean.
    build();
    flushLookups();

    component.form.get('serviceable')!.setValue('true');
    let emitted: Record<string, unknown> | undefined;
    component.changed.subscribe((f) => (emitted = f));
    component.apply();

    expect(emitted).toEqual({ serviceable: true });
  });

  it('passes text and status filters through unchanged', () => {
    build();
    flushLookups();

    component.form.get('status')!.setValue('INACTIVE');
    component.form.get('zone')!.setValue('LOCAL');

    let emitted: Record<string, unknown> | undefined;
    component.changed.subscribe((f) => (emitted = f));
    component.apply();

    expect(emitted).toEqual({ status: 'INACTIVE', zone: 'LOCAL' });
  });

  it('reset clears every filter and says so', () => {
    build();
    flushLookups();

    component.form.get('zone')!.setValue('LOCAL');
    let emitted: Record<string, unknown> | undefined;
    component.changed.subscribe((f) => (emitted = f));
    component.reset();

    expect(emitted).toEqual({});
    expect(component.form.get('zone')!.value).toBeNull();
  });

  it('loads the picker for a lookup filter', () => {
    build(MASTER_DEFINITIONS.states);

    const request = http.expectOne((r) => r.url.includes('/global-masters/countries'));
    expect(request.request.params.get('status')).toBe('ACTIVE');
    request.flush({
      success: true,
      data: {
        content: [{ id: 'c-1', code: 'INDIA', name: 'India' }],
        page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true, hasNext: false
      }
    });

    expect(component.lookupOptions({ key: 'countryId', label: 'Country', kind: 'lookup' }))
      .toEqual([{ value: 'c-1', label: 'India (INDIA)' }]);
  });

  it('leaves the filter empty when its picker fails rather than blocking the drawer', () => {
    build(MASTER_DEFINITIONS.states);

    http.expectOne((r) => r.url.includes('/global-masters/countries'))
      .flush('nope', { status: 500, statusText: 'Server Error' });

    expect(component.lookupOptions({ key: 'countryId', label: 'Country', kind: 'lookup' })).toEqual([]);
    expect(component.form.get('countryId')).toBeTruthy();
  });
});
