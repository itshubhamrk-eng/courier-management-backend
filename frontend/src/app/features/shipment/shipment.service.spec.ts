import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { ShipmentService } from './shipment.service';

/** Shipment Booking's own CRUD, cancel, sub-resources and the Pricing Engine preview call. */
describe('ShipmentService', () => {
  const base = environment.apiBaseUrl;
  let service: ShipmentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ShipmentService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ShipmentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('books a shipment with POST to the collection', () => {
    service.create({
      bookingBranchId: 'b-1', deliveryBranchId: 'b-2', pickupPincode: '411001', deliveryPincode: '400008',
      senderName: 'Asha Shah', senderAddress: '221B Baker Street, Pune', senderContact: '9876543210',
      receiverName: 'Rahul Verma', receiverAddress: '12 MG Road, Mumbai', receiverContact: '9876500000',
      serviceTypeId: 's-1', packageTypeId: 'p-1',
      paymentModeId: 'pm-1', items: [{ itemName: 'Box', weight: 2 }]
    }).subscribe();

    const request = http.expectOne(`${base}/shipments`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.senderName).toBe('Asha Shah');
    request.flush({ success: true, data: { id: 'shp-1', shipmentNumber: 'SHP1', trackingNumber: 'AWB1' } });
  });

  it('updates with PUT carrying the version', () => {
    service.update('shp-1', {
      version: 3, deliveryBranchId: 'b-2', pickupPincode: '411001', deliveryPincode: '400008',
      senderName: 'Asha Shah', senderAddress: '221B Baker Street, Pune', senderContact: '9876543210',
      receiverName: 'Rahul Verma', receiverAddress: '12 MG Road, Mumbai', receiverContact: '9876500000',
      serviceTypeId: 's-1', packageTypeId: 'p-1',
      paymentModeId: 'pm-1', items: [{ itemName: 'Box', weight: 2 }]
    }).subscribe();

    const request = http.expectOne(`${base}/shipments/shp-1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.version).toBe(3);
    request.flush({ success: true, data: {} });
  });

  it('fetches by tracking number through the dedicated track route, not a second /shipments/{x}', () => {
    service.getByTrackingNumber('AWB2607300000001').subscribe();
    const request = http.expectOne(`${base}/shipments/track/AWB2607300000001`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: {} });
  });

  it('cancels with an optional remarks query param', () => {
    service.cancel('shp-1', 'changed my mind').subscribe();
    const request = http.expectOne(`${base}/shipments/shp-1/cancel?remarks=changed%20my%20mind`);
    expect(request.request.method).toBe('POST');
    request.flush({ success: true, data: {} });
  });

  it('reads charges, history and documents off their own sub-resource routes', () => {
    service.charges('shp-1').subscribe();
    http.expectOne(`${base}/shipments/shp-1/charges`).flush({ success: true, data: {} });

    service.history('shp-1').subscribe();
    http.expectOne(`${base}/shipments/shp-1/history`).flush({ success: true, data: [] });

    service.documents('shp-1').subscribe();
    http.expectOne(`${base}/shipments/shp-1/documents`).flush({ success: true, data: [] });
  });

  it('attaches a document with POST to the documents sub-resource', () => {
    service.addDocument('shp-1', {
      documentType: 'INVOICE', documentName: 'Invoice.pdf', documentUrl: 'https://example.com/invoice.pdf'
    }).subscribe();

    const request = http.expectOne(`${base}/shipments/shp-1/documents`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.documentType).toBe('INVOICE');
    request.flush({ success: true, data: {} });
  });

  it('previews pricing through the Pricing Engine, not a shipment endpoint of its own', () => {
    service.preview({
      bookingBranchId: 'b-1', deliveryBranchId: 'b-2', pickupPincode: '411001', deliveryPincode: '400008',
      serviceTypeId: 's-1', packageTypeId: 'p-1', paymentModeId: 'pm-1', actualWeight: 5
    }).subscribe();

    const request = http.expectOne(`${base}/pricing/calculate`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.pickupPincode).toBe('411001');
    request.flush({ success: true, data: {} });
  });
});
