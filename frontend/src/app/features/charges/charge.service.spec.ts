import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { ChargeService } from './charge.service';

/** Charge CRUD, lifecycle, delete, and the Charge Setting sub-resource. */
describe('ChargeService', () => {
  const base = environment.apiBaseUrl;
  let service: ChargeService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ChargeService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ChargeService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a charge with POST to the collection', () => {
    service.create({ chargeName: 'Fuel Surcharge', serviceTypeId: 's-1' }).subscribe();

    const request = http.expectOne(`${base}/charges`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.chargeName).toBe('Fuel Surcharge');
    request.flush({ success: true, data: { id: 'charge-1' } });
  });

  it('updates with PUT carrying the version', () => {
    service.update('charge-1', { chargeName: 'Updated', serviceTypeId: 's-1', version: 2 }).subscribe();

    const request = http.expectOne(`${base}/charges/charge-1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.version).toBe(2);
    request.flush({ success: true, data: {} });
  });

  it('activates and deactivates with their own PATCH endpoints', () => {
    service.activate('charge-1').subscribe();
    const activate = http.expectOne(`${base}/charges/charge-1/activate`);
    expect(activate.request.method).toBe('PATCH');
    activate.flush({ success: true, data: {} });

    service.deactivate('charge-1').subscribe();
    const deactivate = http.expectOne(`${base}/charges/charge-1/deactivate`);
    expect(deactivate.request.method).toBe('PATCH');
    deactivate.flush({ success: true, data: {} });
  });

  it('deletes with DELETE to the resource', () => {
    service.delete('charge-1').subscribe();
    const request = http.expectOne(`${base}/charges/charge-1`);
    expect(request.request.method).toBe('DELETE');
    request.flush({ success: true });
  });

  it('lists a charge\'s settings with GET to the sub-resource', () => {
    service.listSettings('charge-1').subscribe();
    const request = http.expectOne(`${base}/charges/charge-1/settings`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: [] });
  });

  it('creates a charge setting with POST to the sub-resource', () => {
    service.createSetting('charge-1', {
      chargeType: 'SLAB', chargeSlabType: 'KG', fromKg: 0, toKg: 5,
      chargeValue: 50, chargeValueType: 'AMOUNT', commissionType: 'AMOUNT', commissionValue: 0
    }).subscribe();

    const request = http.expectOne(`${base}/charges/charge-1/settings`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.chargeSlabType).toBe('KG');
    request.flush({ success: true, data: { id: 'setting-1' } });
  });

  it('updates a charge setting with PUT carrying the version', () => {
    service.updateSetting('charge-1', 'setting-1', {
      chargeType: 'FACTOR', chargeValue: 10, chargeValueType: 'PERCENTAGE',
      commissionType: 'AMOUNT', commissionValue: 0, version: 3
    }).subscribe();

    const request = http.expectOne(`${base}/charges/charge-1/settings/setting-1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.version).toBe(3);
    request.flush({ success: true, data: {} });
  });

  it('deletes a charge setting with DELETE to the sub-resource', () => {
    service.deleteSetting('charge-1', 'setting-1').subscribe();
    const request = http.expectOne(`${base}/charges/charge-1/settings/setting-1`);
    expect(request.request.method).toBe('DELETE');
    request.flush({ success: true });
  });

  it('activates and deactivates a charge setting with their own PATCH endpoints', () => {
    service.activateSetting('charge-1', 'setting-1').subscribe();
    const activate = http.expectOne(`${base}/charges/charge-1/settings/setting-1/activate`);
    expect(activate.request.method).toBe('PATCH');
    activate.flush({ success: true, data: {} });

    service.deactivateSetting('charge-1', 'setting-1').subscribe();
    const deactivate = http.expectOne(`${base}/charges/charge-1/settings/setting-1/deactivate`);
    expect(deactivate.request.method).toBe('PATCH');
    deactivate.flush({ success: true, data: {} });
  });
});
