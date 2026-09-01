import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { CustomerService } from './customer.service';

/**
 * The customer CRUD, lifecycle and address sub-resource calls, plus the geography
 * cascade reading from the shared global masters rather than anything company-scoped.
 */
describe('CustomerService', () => {
  const base = environment.apiBaseUrl;
  let service: CustomerService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CustomerService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CustomerService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates a customer with POST to the collection', () => {
    service.create({
      customerType: 'INDIVIDUAL', firstName: 'Asha', lastName: 'Shah', mobile: '9876543210'
    }).subscribe();

    const request = http.expectOne(`${base}/customers`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.mobile).toBe('9876543210');
    request.flush({ success: true, data: { id: 'c-1' } });
  });

  it('updates with PUT carrying the version', () => {
    service.update('c-1', {
      customerType: 'INDIVIDUAL', firstName: 'Asha', lastName: 'Shah', mobile: '9876543210',
      whatsappEnabled: true, smsEnabled: true, emailEnabled: true, version: 2
    }).subscribe();

    const request = http.expectOne(`${base}/customers/c-1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.version).toBe(2);
    request.flush({ success: true, data: {} });
  });

  it('activates and deactivates with their own PATCH endpoints', () => {
    service.activate('c-1').subscribe();
    const activate = http.expectOne(`${base}/customers/c-1/activate`);
    expect(activate.request.method).toBe('PATCH');
    activate.flush({ success: true, data: {} });

    service.deactivate('c-1').subscribe();
    const deactivate = http.expectOne(`${base}/customers/c-1/deactivate`);
    expect(deactivate.request.method).toBe('PATCH');
    deactivate.flush({ success: true, data: {} });
  });

  it('adds an address as a sub-resource of the customer', () => {
    service.addAddress('c-1', { addressType: 'HOME', addressLine1: 'MG Road' }).subscribe();

    const request = http.expectOne(`${base}/customers/c-1/addresses`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.addressLine1).toBe('MG Road');
    request.flush({ success: true, data: { id: 'a-1' } });
  });

  it('updates an address with PUT to its own id', () => {
    service.updateAddress('c-1', 'a-1', {
      addressType: 'OFFICE', addressLine1: 'MG Road', version: 1
    }).subscribe();

    const request = http.expectOne(`${base}/customers/c-1/addresses/a-1`);
    expect(request.request.method).toBe('PUT');
    request.flush({ success: true, data: {} });
  });

  it('deletes an address with DELETE to its own id', () => {
    service.removeAddress('c-1', 'a-1').subscribe();

    const request = http.expectOne(`${base}/customers/c-1/addresses/a-1`);
    expect(request.request.method).toBe('DELETE');
    request.flush({ success: true, data: null });
  });

  it('reads states from the shared global masters, filtered by country, not a company path', () => {
    service.states('country-1').subscribe();

    const request = http.expectOne(
      (r) => r.url === `${base}/global-masters/states` && r.params.get('countryId') === 'country-1'
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      success: true,
      data: { content: [{ id: 's-1', code: 'MH', name: 'Maharashtra' }], page: 0, size: 200, totalElements: 1, totalPages: 1, first: true, last: true, hasNext: false }
    });
  });
});
