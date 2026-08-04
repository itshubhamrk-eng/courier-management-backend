import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { CompanyService } from './company.service';

/**
 * The company lifecycle and subscription calls.
 *
 * <p>These assert the URL, the verb and the body shape. That is the whole contract with
 * the backend for endpoints that suspend a paying customer or open a paid window, and a
 * typo in any of the three is the kind of bug a component test would never see.
 */
describe('CompanyService', () => {
  const base = environment.apiBaseUrl;
  let service: CompanyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CompanyService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CompanyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('deactivates with PATCH and a nullable reason, distinct from suspend', () => {
    // Two endpoints, not one with a flag: deactivation is housekeeping and suspension is
    // punitive, and the customer is told different things about each.
    service.deactivate('c-1').subscribe();

    const request = http.expectOne(`${base}/companies/c-1/deactivate`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ reason: null });
    request.flush({ success: true, data: {} });
  });

  it('carries the reason when one is given', () => {
    service.deactivate('c-1', 'Dormant since March').subscribe();

    const request = http.expectOne(`${base}/companies/c-1/deactivate`);
    expect(request.request.body).toEqual({ reason: 'Dormant since March' });
    request.flush({ success: true, data: {} });
  });

  it('assigns a subscription with POST to the subscription sub-resource', () => {
    service.assignSubscription('c-1', {
      subscriptionPlanId: 'plan-1', billingCycle: 'YEARLY', periods: 1
    }).subscribe();

    const request = http.expectOne(`${base}/companies/c-1/subscription`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.subscriptionPlanId).toBe('plan-1');
    request.flush({ success: true, data: {} });
  });

  it('renews without ever sending a start date', () => {
    // The server extends from the later of the current end and today. A start date in the
    // request would let the caller forfeit days the customer already paid for.
    service.renewSubscription('c-1', { billingCycle: 'MONTHLY', periods: 3 }).subscribe();

    const request = http.expectOne(`${base}/companies/c-1/subscription/renew`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).not.toHaveProperty('startDate');
    request.flush({ success: true, data: {} });
  });

  it('suspends a subscription with the reason the endpoint demands', () => {
    service.suspendSubscription('c-1', 'Chargeback').subscribe();

    const request = http.expectOne(`${base}/companies/c-1/subscription/suspend`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Chargeback' });
    request.flush({ success: true, data: {} });
  });

  it('reads company statistics, which carry no shipment figure', () => {
    let received: Record<string, unknown> | undefined;
    service.statistics('c-1').subscribe((s) => (received = s as never));

    http.expectOne(`${base}/companies/c-1/statistics`).flush({
      success: true,
      data: { id: 'c-1', userCount: 4, branchCount: 2, daysToExpiry: 12 }
    });

    // Asserted rather than assumed: a zero shipment count would read as "booked nothing"
    // and is exactly what this API deliberately omits until the module exists.
    expect(received).not.toHaveProperty('shipmentCount');
    expect(received!['userCount']).toBe(4);
  });

  it('reads the platform dashboard from the super-admin console', () => {
    service.platformDashboard().subscribe();

    const request = http.expectOne(`${base}/super-admin/dashboard`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: { companyCount: 0 } });
  });

  it('creates a company with POST to the collection', () => {
    service.create({
      companyCode: 'ACME', companyName: 'Acme', subscriptionPlanId: 'plan-1',
      email: 'ops@acme.test', mobile: '+91 9876543210'
    }).subscribe();

    const request = http.expectOne(`${base}/companies`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.companyCode).toBe('ACME');
    request.flush({ success: true, data: { id: 'c-9' } });
  });
});
