import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { beforeEach, describe, expect, it } from 'vitest';
import { MasterRecord } from '@core/models/master.model';
import { MasterForm } from './master-form';
import { MASTER_DEFINITIONS } from '../master.config';

const countries = MASTER_DEFINITIONS.countries;
const weightSlabs = MASTER_DEFINITIONS['weight-slabs'];

const record = (over: Partial<MasterRecord> = {}): MasterRecord => ({
  id: 'id-1', companyId: 't-1', code: 'INDIA', name: 'India', description: 'Domestic',
  status: 'ACTIVE', displayOrder: 10, createdBy: null, createdDate: null,
  updatedBy: null, updatedDate: null, version: 7,
  isoCode2: 'IN', isoCode3: 'IND', dialCode: '+91', currencyCode: 'INR', ...over
});

describe('MasterForm', () => {
  let fixture: ComponentFixture<MasterForm>;
  let component: MasterForm;
  let http: HttpTestingController;

  const build = (def = countries, existing: MasterRecord | null = null) => {
    fixture = TestBed.createComponent(MasterForm);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('def', def);
    fixture.componentRef.setInput('record', existing);
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MasterForm],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('builds a control for every declared field', () => {
    build();
    for (const field of countries.fields) {
      expect(component.form.get(field.key), field.key).toBeTruthy();
    }
  });

  it('mirrors the DTO’s validators, so a bad code is caught before the round trip', () => {
    build();
    const code = component.controlFor('code');

    code.setValue('');
    expect(code.hasError('required')).toBe(true);

    code.setValue('!!');
    expect(code.hasError('pattern')).toBe(true);

    code.setValue('INDIA');
    expect(code.valid).toBe(true);
  });

  it('refuses to emit while the form is invalid, and marks the fields so errors show', () => {
    build();
    let emitted = false;
    component.saved.subscribe(() => (emitted = true));

    component.submit();

    expect(emitted).toBe(false);
    expect(component.controlFor('name').touched).toBe(true);
  });

  it('emits a create payload with no version', () => {
    build();
    component.controlFor('code').setValue('nepal');
    component.controlFor('name').setValue('Nepal');

    let payload: Record<string, unknown> | undefined;
    component.saved.subscribe((p) => (payload = p));
    component.submit();

    expect(payload).toBeDefined();
    expect(payload!['code']).toBe('nepal');
    expect(payload!['version']).toBeUndefined();
  });

  it('disables the code in edit mode rather than hiding it', () => {
    // Hiding it would leave the user wondering where it went; greyed out says "permanent",
    // which is the actual rule.
    build(countries, record());

    expect(component.controlFor('code').disabled).toBe(true);
    expect(component.controlFor('name').disabled).toBe(false);
  });

  it('omits the code from an edit payload and carries the version', () => {
    build(countries, record({ version: 7 }));
    component.controlFor('name').setValue('Republic of India');

    let payload: Record<string, unknown> | undefined;
    component.saved.subscribe((p) => (payload = p));
    component.submit();

    expect(payload!['code']).toBeUndefined();
    expect(payload!['name']).toBe('Republic of India');
    expect(payload!['version']).toBe(7);
  });

  it('sends null for an emptied optional field, not an empty string', () => {
    // So "cleared" and "never set" are the same value in the database.
    build(countries, record({ dialCode: '+91' }));
    component.controlFor('dialCode').setValue('');

    let payload: Record<string, unknown> | undefined;
    component.saved.subscribe((p) => (payload = p));
    component.submit();

    expect(payload!['dialCode']).toBeNull();
  });

  it('starts a pincode’s availability toggles on, and always sends real booleans', () => {
    // A toggle has no "unset" state to show. Starting them off would mean every pincode
    // created through the UI arrives unserviceable — the opposite of why it was added.
    build(MASTER_DEFINITIONS.pincodes);
    component.controlFor('code').setValue('411038');
    component.controlFor('name').setValue('Kothrud H.O.');
    component.controlFor('areaId').setValue('area-1');

    let payload: Record<string, unknown> | undefined;
    component.saved.subscribe((p) => (payload = p));
    component.submit();

    expect(payload!['serviceable']).toBe(true);
    expect(payload!['codAvailable']).toBe(true);
    expect(typeof payload!['pickupAvailable']).toBe('boolean');

    // The area picker was requested when the form opened.
    http.match((r) => r.url.includes('/master/areas')).forEach((r) =>
      r.flush({ success: true, data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true, hasNext: false } })
    );
  });

  it('groups fields the way the definition declares them', () => {
    build(weightSlabs);
    const groups = component.groups().map((g) => g.name);

    expect(groups).toEqual(['Identity', 'Band']);
    expect(component.groups()[1].fields.map((f) => f.key))
      .toEqual(['minWeight', 'maxWeight', 'weightUnit']);
  });

  it('requires both ends of a weight band', () => {
    build(weightSlabs);
    expect(component.controlFor('minWeight').hasError('required')).toBe(true);
    expect(component.controlFor('maxWeight').hasError('required')).toBe(true);
  });
});
