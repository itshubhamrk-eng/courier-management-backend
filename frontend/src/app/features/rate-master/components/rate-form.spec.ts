import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { of } from 'rxjs';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { emptyPage } from '@core/models/page.model';
import { CreateRateRequest } from '@core/models/rate.model';
import { RateService } from '../rate.service';
import { RateForm } from './rate-form';

/**
 * The half-open weight range and effective-window validators mirror the server's
 * `Rate.applyInvariants()` — the client-side half, so the error surfaces before the
 * round-trip. `RateService.siblings` is stubbed since the form fetches the Weight Slab
 * Grid reactively as soon as the four combination fields are filled.
 */
describe('RateForm — validators mirror server invariants', () => {
  let fixture: ComponentFixture<RateForm>;
  let form: RateForm;

  const group = () => (form as unknown as {
    form: { patchValue: (v: object) => void; get: (n: string) => { valid: boolean }; invalid: boolean };
  }).form;

  const emitted = (): CreateRateRequest | undefined => {
    let body: CreateRateRequest | undefined;
    fixture.componentInstance.saved.subscribe((b) => (body = b as CreateRateRequest));
    (form as unknown as { submit: () => void }).submit();
    return body;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RateForm],
      providers: [
        provideNoopAnimations(),
        { provide: RateService, useValue: { siblings: () => of(emptyPage()) } }
      ]
    });
    fixture = TestBed.createComponent(RateForm);
    fixture.componentRef.setInput('mode', 'create');
    form = fixture.componentInstance;
    fixture.detectChanges();

    group().patchValue({
      rateCode: 'RATE1', rateName: 'Pune-Mumbai Standard',
      routeId: 'r-1', serviceTypeId: 's-1', packageTypeId: 'p-1', paymentModeId: 'pm-1',
      minimumWeight: 0, maximumWeight: 5, weightUnit: 'KG',
      baseRate: 100, additionalWeight: 0.5, additionalWeightRate: 20,
      minimumCharge: 0, fuelSurcharge: 0, handlingCharge: 0, odaCharge: 0, insuranceCharge: 0,
      gstPercentage: 18, effectiveFrom: '2026-01-01'
    });
  });

  it('accepts a valid slab and emits the create request, uppercasing the code', () => {
    fixture.detectChanges();

    const body = emitted();
    expect(body).toBeDefined();
    expect(body!.rateCode).toBe('RATE1');
    expect(body!.minimumWeight).toBe(0);
    expect(body!.maximumWeight).toBe(5);
  });

  it('refuses a maximum weight that is not greater than the minimum', () => {
    group().patchValue({ minimumWeight: 5, maximumWeight: 5 });
    fixture.detectChanges();

    expect(emitted()).toBeUndefined();
  });

  it('refuses a maximum weight below the minimum', () => {
    group().patchValue({ minimumWeight: 5, maximumWeight: 1 });
    fixture.detectChanges();

    expect(emitted()).toBeUndefined();
  });

  it('refuses an effective-to date before effective-from', () => {
    group().patchValue({ effectiveFrom: '2026-06-01', effectiveTo: '2026-01-01' });
    fixture.detectChanges();

    expect(emitted()).toBeUndefined();
  });

  it('accepts a blank effective-to as open-ended', () => {
    group().patchValue({ effectiveFrom: '2026-06-01', effectiveTo: '' });
    fixture.detectChanges();

    const body = emitted();
    expect(body).toBeDefined();
    expect(body!.effectiveTo).toBeNull();
  });

  it('refuses a zero additional weight increment', () => {
    group().patchValue({ additionalWeight: 0 });
    fixture.detectChanges();

    expect(emitted()).toBeUndefined();
  });

  it('refuses GST above 100', () => {
    group().patchValue({ gstPercentage: 101 });
    fixture.detectChanges();

    expect(emitted()).toBeUndefined();
  });
});
