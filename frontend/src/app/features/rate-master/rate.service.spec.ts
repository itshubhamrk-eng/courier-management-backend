import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { RateService } from './rate.service';

/** The rate CRUD, lifecycle, sibling-slab lookup and the calculate call. */
describe('RateService', () => {
  const base = environment.apiBaseUrl;
  let service: RateService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RateService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RateService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a rate with POST to the collection', () => {
    service.create({
      rateCode: 'RATE1', rateName: 'Pune-Mumbai Standard', routeId: 'r-1', serviceTypeId: 's-1',
      packageTypeId: 'p-1', paymentModeId: 'pm-1', minimumWeight: 0, maximumWeight: 5,
      weightUnit: 'KG', baseRate: 100, additionalWeight: 0.5, additionalWeightRate: 20,
      minimumCharge: 0, fuelSurcharge: 0, handlingCharge: 0, odaCharge: 0, insuranceCharge: 0,
      gstPercentage: 18, effectiveFrom: '2026-01-01'
    }).subscribe();

    const request = http.expectOne(`${base}/rates`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.rateCode).toBe('RATE1');
    request.flush({ success: true, data: { id: 'rate-1' } });
  });

  it('updates with PUT carrying the version', () => {
    service.update('rate-1', {
      rateName: 'Updated', routeId: 'r-1', serviceTypeId: 's-1', packageTypeId: 'p-1',
      paymentModeId: 'pm-1', minimumWeight: 0, maximumWeight: 5, weightUnit: 'KG',
      baseRate: 100, additionalWeight: 0.5, additionalWeightRate: 20, minimumCharge: 0,
      fuelSurcharge: 0, handlingCharge: 0, odaCharge: 0, insuranceCharge: 0,
      gstPercentage: 18, effectiveFrom: '2026-01-01', version: 2
    }).subscribe();

    const request = http.expectOne(`${base}/rates/rate-1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.version).toBe(2);
    request.flush({ success: true, data: {} });
  });

  it('activates and deactivates with their own PATCH endpoints', () => {
    service.activate('rate-1').subscribe();
    const activate = http.expectOne(`${base}/rates/rate-1/activate`);
    expect(activate.request.method).toBe('PATCH');
    activate.flush({ success: true, data: {} });

    service.deactivate('rate-1').subscribe();
    const deactivate = http.expectOne(`${base}/rates/rate-1/deactivate`);
    expect(deactivate.request.method).toBe('PATCH');
    deactivate.flush({ success: true, data: {} });
  });

  it('reads sibling slabs filtered by the full combination', () => {
    service.siblings('r-1', 's-1', 'p-1', 'pm-1').subscribe();

    const request = http.expectOne((r) =>
      r.url === `${base}/rates`
      && r.params.get('routeId') === 'r-1'
      && r.params.get('serviceTypeId') === 's-1'
      && r.params.get('packageTypeId') === 'p-1'
      && r.params.get('paymentModeId') === 'pm-1');
    expect(request.request.method).toBe('GET');
    request.flush({
      success: true,
      data: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, first: true, last: true, hasNext: false }
    });
  });

  it('calculates a rate with POST to /rates/calculate', () => {
    service.calculate({
      bookingBranchId: 'b-1', deliveryBranchId: 'b-2', serviceTypeId: 's-1',
      packageTypeId: 'p-1', paymentModeId: 'pm-1', actualWeight: 2.5
    }).subscribe();

    const request = http.expectOne(`${base}/rates/calculate`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.actualWeight).toBe(2.5);
    request.flush({
      success: true,
      data: {
        matchedRateId: 'rate-1', matchedRateCode: 'RATE1', matchedRateName: 'Pune-Mumbai Standard',
        chargeableWeight: 2.5, weightUnit: 'KG', freight: 100, fuelSurcharge: 0, handlingCharge: 0,
        odaCharge: 0, insuranceCharge: 0, gstPercentage: 18, gstAmount: 18, totalAmount: 118
      }
    });
  });
});
