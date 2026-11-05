import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { SecurityIncidentService } from './security-incident.service';

describe('SecurityIncidentService', () => {
  const base = `${environment.apiBaseUrl}/security/incidents`;
  let service: SecurityIncidentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SecurityIncidentService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lists with only the filters that are set, as query params', () => {
    service.list({ status: 'OPEN', rule: 'CREDENTIAL_ATTACK', page: 2, size: 25 }).subscribe();
    const req = http.expectOne((r) => r.url === base);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('rule')).toBe('CREDENTIAL_ATTACK');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
    expect(req.request.params.has('severity')).toBe(false);
    req.flush({ data: { content: [] } });
  });

  it('gets one incident by id', () => {
    service.get('i-1').subscribe();
    const req = http.expectOne(`${base}/i-1`);
    expect(req.request.method).toBe('GET');
    req.flush({ data: {} });
  });

  it('reads the summary', () => {
    let open = -1;
    service.summary().subscribe((s) => (open = s.OPEN));
    http.expectOne(`${base}/summary`).flush({ data: { OPEN: 3, BANNED: 0, DISMISSED: 1, UNBANNED: 0 } });
    expect(open).toBe(3);
  });

  it.each(['ban', 'dismiss', 'unban'] as const)('%s POSTs the note to its own endpoint', (action) => {
    service[action]('i-1', 'because').subscribe();
    const req = http.expectOne(`${base}/i-1/${action}`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ note: 'because' });
    req.flush({ data: {} });
  });

  it('sends a null note when none is given', () => {
    service.dismiss('i-1').subscribe();
    const req = http.expectOne(`${base}/i-1/dismiss`);
    expect(req.request.body).toEqual({ note: null });
    req.flush({ data: {} });
  });
});
