import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CreateDistrictLevelFreightRequest } from '@core/models/district-level-freight.model';
import { DistrictFreightForm } from './district-freight-form';

/**
 * Validators mirror the server's `DistrictLevelFreight.applyInvariants()` — required
 * branch/district, all six slab rates required and non-negative.
 */
describe('DistrictFreightForm — validators mirror server invariants', () => {
  let fixture: ComponentFixture<DistrictFreightForm>;
  let form: DistrictFreightForm;

  const group = () => (form as unknown as {
    form: { patchValue: (v: object) => void; invalid: boolean };
  }).form;

  const emitted = (): CreateDistrictLevelFreightRequest | undefined => {
    let body: CreateDistrictLevelFreightRequest | undefined;
    fixture.componentInstance.saved.subscribe((b) => (body = b as CreateDistrictLevelFreightRequest));
    (form as unknown as { submit: () => void }).submit();
    return body;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DistrictFreightForm],
      providers: [provideNoopAnimations()]
    });
    fixture = TestBed.createComponent(DistrictFreightForm);
    fixture.componentRef.setInput('mode', 'create');
    form = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('refuses submit with no branch/district or rates filled', () => {
    fixture.detectChanges();
    expect(emitted()).toBeUndefined();
  });

  it('accepts a fully filled slab and emits the create request', () => {
    group().patchValue({
      branchId: 'b-1', districtId: 'd-1',
      rate1To15: 10, rate16To50: 8.5, rate51To100: 8, rate101To1000: 7.5,
      rate1001To1500: 6, rate1501To2000: 6, odaApplicable: true, odaCharge: 250
    });
    fixture.detectChanges();

    const body = emitted();
    expect(body).toBeDefined();
    expect(body!.branchId).toBe('b-1');
    expect(body!.districtId).toBe('d-1');
    expect(body!.rate1To15).toBe(10);
    expect(body!.odaCharge).toBe(250);
  });

  it('refuses a negative slab rate', () => {
    group().patchValue({
      branchId: 'b-1', districtId: 'd-1',
      rate1To15: -1, rate16To50: 8.5, rate51To100: 8, rate101To1000: 7.5,
      rate1001To1500: 6, rate1501To2000: 6
    });
    fixture.detectChanges();

    expect(emitted()).toBeUndefined();
  });

  const rates = { rate1To15: 10, rate16To50: 8.5, rate51To100: 8, rate101To1000: 7.5, rate1001To1500: 6, rate1501To2000: 6 };

  it('State level: needs a state, not a district, and emits stateId only', () => {
    group().patchValue({ level: 'STATE', branchId: 'b-1', stateId: 's-1', ...rates });
    fixture.detectChanges();

    const body = emitted();
    expect(body).toBeDefined();
    expect(body!.stateId).toBe('s-1');
    expect(body!.districtId).toBeNull();
  });

  it('State level: refuses a missing state', () => {
    group().patchValue({ level: 'STATE', branchId: 'b-1', stateId: null, ...rates });
    fixture.detectChanges();

    expect(emitted()).toBeUndefined();
  });

  it('District level: still needs a district and never sends the narrowing state as stateId', () => {
    group().patchValue({ level: 'DISTRICT', branchId: 'b-1', stateId: 's-1', districtId: 'd-1', ...rates });
    fixture.detectChanges();

    const body = emitted();
    expect(body!.districtId).toBe('d-1');
    expect(body!.stateId).toBeNull();
  });

  it('edit of a state-level row opens on the State level with its state selected', () => {
    const edit = TestBed.createComponent(DistrictFreightForm);
    edit.componentRef.setInput('mode', 'edit');
    edit.componentRef.setInput('row', {
      id: 'r-1', companyId: 'c', branchId: 'b-1', branchCode: null, branchName: null,
      districtId: null, districtCode: null, districtName: null,
      stateId: 's-1', stateCode: 'MH', stateName: 'Maharashtra', level: 'STATE',
      ...rates, odaApplicable: true, odaCharge: 250, status: 'ACTIVE', version: 3
    });
    edit.detectChanges();

    const g = (edit.componentInstance as unknown as { form: { getRawValue: () => Record<string, unknown> } }).form;
    expect(g.getRawValue()['level']).toBe('STATE');
    expect(g.getRawValue()['stateId']).toBe('s-1');
  });
});
