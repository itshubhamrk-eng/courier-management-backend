import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { CommunicationService } from './communication.service';

/** The HTTP contract per endpoint — templates, settings, logs, dashboard — mirroring
 *  the four backend controllers one-to-one. See MEMORY/modules/communication.md. */
describe('CommunicationService', () => {
  const base = environment.apiBaseUrl;
  let service: CommunicationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CommunicationService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(CommunicationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists templates with GET to the collection', () => {
    service.listTemplates().subscribe();
    const request = http.expectOne(`${base}/communication/templates`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: [] });
  });

  it('creates a template with POST', () => {
    service.createTemplate({
      eventType: 'SHIPMENT_CANCELLED', channel: 'SMS', templateName: 'Cancelled SMS', content: 'Sorry.'
    }).subscribe();
    const request = http.expectOne(`${base}/communication/templates`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.eventType).toBe('SHIPMENT_CANCELLED');
    request.flush({ success: true, data: { id: 't-1' } });
  });

  it('updates a template with PUT carrying the version', () => {
    service.updateTemplate('t-1', {
      templateName: 'x', content: 'y', status: 'INACTIVE', version: 3
    }).subscribe();
    const request = http.expectOne(`${base}/communication/templates/t-1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.version).toBe(3);
    request.flush({ success: true, data: {} });
  });

  it('previews a template with GET to its own sub-path', () => {
    service.previewTemplate('t-1').subscribe();
    const request = http.expectOne(`${base}/communication/templates/t-1/preview`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: { content: 'Hi Rahul' } });
  });

  it('lists channel settings with GET to the collection', () => {
    service.listSettings().subscribe();
    const request = http.expectOne(`${base}/communication/settings`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: [] });
  });

  it('upserts a channel setting with PUT to its own channel path', () => {
    service.upsertSetting('WHATSAPP', {
      enabled: true, provider: 'META_CLOUD_API', config: { phoneNumberId: '123' }, secret: 'token'
    }).subscribe();
    const request = http.expectOne(`${base}/communication/settings/WHATSAPP`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.enabled).toBe(true);
    request.flush({ success: true, data: {} });
  });

  it('tests a channel connection with POST to its own sub-path', () => {
    service.testConnection('SMS').subscribe();
    const request = http.expectOne(`${base}/communication/settings/SMS/test-connection`);
    expect(request.request.method).toBe('POST');
    request.flush({ success: true, data: { ok: true, message: 'ok' } });
  });

  it('searches logs with filters as query params', () => {
    service.searchLogs({ channel: 'EMAIL', status: 'FAILED' }, { page: 0, size: 20 }).subscribe();
    const request = http.expectOne(
      (r) => r.url === `${base}/communication/logs`
        && r.params.get('channel') === 'EMAIL' && r.params.get('status') === 'FAILED'
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      success: true,
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true, hasNext: false }
    });
  });

  it('fetches every log for one shipment', () => {
    service.logsForShipment('s-1').subscribe();
    const request = http.expectOne(`${base}/communication/logs/shipment/s-1`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: [] });
  });

  it('retries a failed log with POST to its own sub-path', () => {
    service.retry('l-1').subscribe();
    const request = http.expectOne(`${base}/communication/logs/l-1/retry`);
    expect(request.request.method).toBe('POST');
    request.flush({ success: true, data: {} });
  });

  it('reads today\'s dashboard with GET', () => {
    service.dashboard().subscribe();
    const request = http.expectOne(`${base}/communication/dashboard`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: { totalSent: 0, totalDelivered: 0, totalFailed: 0, totalPending: 0, channels: {} } });
  });
});
