import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { ManifestService } from './manifest.service';

describe('ManifestService', () => {
  const base = environment.apiBaseUrl;
  let service: ManifestService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ManifestService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ManifestService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a manifest with POST to the collection', () => {
    service.create({ bookingBranchId: 'b-1', deliveryBranchId: 'b-2', shipmentIds: ['shp-1'] }).subscribe();
    const request = http.expectOne(`${base}/manifests`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.shipmentIds).toEqual(['shp-1']);
    request.flush({ success: true, data: { id: 'mft-1', manifestNumber: 'MFT-1' } });
  });

  it('lists the shipments scanned onto a manifest via its own sub-resource', () => {
    service.shipments('mft-1').subscribe();
    const request = http.expectOne(`${base}/manifests/mft-1/shipments`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: [] });
  });
});
