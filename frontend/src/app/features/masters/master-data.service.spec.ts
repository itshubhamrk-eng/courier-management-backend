import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { environment } from '@env/environment';
import { Page } from '@core/models/page.model';
import { MasterRecord } from '@core/models/master.model';
import { MasterDataService } from './master-data.service';
import { MASTER_DEFINITIONS } from './master.config';

const base = environment.apiBaseUrl;
const countries = MASTER_DEFINITIONS.countries;
const states = MASTER_DEFINITIONS.states;

const row = (over: Partial<MasterRecord> = {}): MasterRecord => ({
  id: 'id-1', companyId: 't-1', code: 'INDIA', name: 'India', description: null,
  status: 'ACTIVE', displayOrder: 0, createdBy: null, createdDate: null,
  updatedBy: null, updatedDate: null, version: 0, ...over
});

const page = (content: MasterRecord[]): Page<MasterRecord> => ({
  content, page: 0, size: 20, totalElements: content.length, totalPages: 1,
  first: true, last: true, hasNext: false
});

describe('MasterDataService', () => {
  let service: MasterDataService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MasterDataService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(MasterDataService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('reads a page from the definition’s path and unwraps the envelope', () => {
    let received: Page<MasterRecord> | undefined;
    service.list(countries, { page: 0, size: 20 }).subscribe((p) => (received = p));

    const request = http.expectOne((r) => r.url === `${base}/global-masters/countries`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: page([row()]) });

    expect(received!.content[0].code).toBe('INDIA');
  });

  it('sends the lifecycle verbs the backend actually exposes', () => {
    service.activate(countries, 'id-1').subscribe();
    const activate = http.expectOne(`${base}/global-masters/countries/id-1/activate`);
    expect(activate.request.method).toBe('PATCH');
    activate.flush({ success: true, data: row({ status: 'ACTIVE' }) });

    service.deactivate(countries, 'id-1').subscribe();
    const deactivate = http.expectOne(`${base}/global-masters/countries/id-1/deactivate`);
    expect(deactivate.request.method).toBe('PATCH');
    deactivate.flush({ success: true, data: row({ status: 'INACTIVE' }) });

    service.remove(countries, 'id-1').subscribe();
    const remove = http.expectOne(`${base}/global-masters/countries/id-1`);
    expect(remove.request.method).toBe('DELETE');
    remove.flush({ success: true, data: null });
  });

  it('updates with PUT, carrying the version the caller read', () => {
    service.update(countries, 'id-1', { name: 'Bharat', version: 3 }).subscribe();

    const request = http.expectOne(`${base}/global-masters/countries/id-1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ version: 3 });
    request.flush({ success: true, data: row({ version: 4 }) });
  });

  it('asks a picker for active rows only', () => {
    // Offering an inactive parent would let a user build a form the backend then refuses
    // with 422 — worse than not seeing it at all.
    service.options('countries').subscribe();

    const request = http.expectOne((r) => r.url === `${base}/global-masters/countries`);
    expect(request.request.params.get('status')).toBe('ACTIVE');
    request.flush({ success: true, data: page([row()]) });
  });

  it('labels picker options with the name and the code', () => {
    let options: { value: string; label: string }[] = [];
    service.options('countries').subscribe((o) => (options = o));

    http.expectOne((r) => r.url === `${base}/global-masters/countries`)
      .flush({ success: true, data: page([row()]) });

    expect(options).toEqual([{ value: 'id-1', label: 'India (INDIA)' }]);
  });

  it('caches picker options so opening five forms is not five requests', () => {
    service.options('states').subscribe();
    http.expectOne((r) => r.url === `${base}/global-masters/states`)
      .flush({ success: true, data: page([row({ code: 'MH', name: 'Maharashtra' })]) });

    let second: { value: string; label: string }[] = [];
    service.options('states').subscribe((o) => (second = o));

    // No second request — verify() in afterEach would fail if one were outstanding.
    expect(second[0].label).toBe('Maharashtra (MH)');
  });

  it('drops the cache after a write, because the new row is usually the next one picked', () => {
    service.options('countries').subscribe();
    http.expectOne((r) => r.url === `${base}/global-masters/countries`)
      .flush({ success: true, data: page([]) });

    service.create(countries, { code: 'NEPAL', name: 'Nepal' }).subscribe();
    http.expectOne((r) => r.method === 'POST' && r.url === `${base}/global-masters/countries`)
      .flush({ success: true, data: row({ code: 'NEPAL' }) });

    service.options('countries').subscribe();
    const refetch = http.expectOne((r) => r.method === 'GET' && r.url === `${base}/global-masters/countries`);
    refetch.flush({ success: true, data: page([row({ code: 'NEPAL' })]) });
  });

  it('reads the branch directory, not a master list, for a route’s two ends', () => {
    // Branches are a different module; the Route master only borrows their identity.
    let options: { value: string; label: string }[] = [];
    service.options('branches').subscribe((o) => (options = o));

    const request = http.expectOne((r) => r.url === `${base}/branches/directory`);
    request.flush({
      success: true,
      data: { ...page([]), content: [{ id: 'b-1', branchCode: 'PUNE', branchName: 'Pune Main', status: 'ACTIVE' }] }
    });

    expect(options).toEqual([{ value: 'b-1', label: 'Pune Main (PUNE)' }]);
  });

  it('posts the bootstrap request with no body of its own', () => {
    let result: { created: Record<string, number> } | undefined;
    service.bootstrap().subscribe((r) => (result = r));

    const request = http.expectOne(`${base}/master/bootstrap`);
    expect(request.request.method).toBe('POST');
    request.flush({ success: true, data: { created: { vehicleTypes: 5 }, skipped: { vehicleTypes: 0 } } });

    expect(result!.created['vehicleTypes']).toBe(5);
  });

  it('uses the right path for each definition', () => {
    service.get(states, 'id-9').subscribe();
    http.expectOne(`${base}/global-masters/states/id-9`).flush({ success: true, data: row() });
  });
});
