import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { HubOperationsService } from './hub-operations.service';

describe('HubOperationsService', () => {
  const base = environment.apiBaseUrl;
  let service: HubOperationsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [HubOperationsService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(HubOperationsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('out-scans a batch of tracking numbers with POST', () => {
    service.outScan({ manifestId: 'm-1', hubBranchId: 'h-1', trackingNumbers: ['TRK-1'] }).subscribe();
    const request = http.expectOne(`${base}/hub-operations/out-scan`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.trackingNumbers).toEqual(['TRK-1']);
    request.flush({ success: true, data: [{ reference: 'TRK-1', success: true }] });
  });

  it('raises an exception with POST', () => {
    service.raiseException({ shipmentId: 's-1', hubBranchId: 'h-1', exceptionType: 'DAMAGED', remarks: 'dented' })
      .subscribe();
    const request = http.expectOne(`${base}/hub-operations/exceptions`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.exceptionType).toBe('DAMAGED');
    request.flush({ success: true, data: { id: 'e-1' } });
  });

  it('resolves an exception with PATCH', () => {
    service.resolveException('e-1', { resolutionRemarks: 'fixed' }).subscribe();
    const request = http.expectOne(`${base}/hub-operations/exceptions/e-1/resolve`);
    expect(request.request.method).toBe('PATCH');
    request.flush({ success: true, data: { id: 'e-1' } });
  });

  it('reads the dashboard scoped to a hub branch', () => {
    service.dashboard('h-1').subscribe();
    const request = http.expectOne(`${base}/hub-operations/dashboard?hubBranchId=h-1`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: { todaysInbound: 0 } });
  });
});
