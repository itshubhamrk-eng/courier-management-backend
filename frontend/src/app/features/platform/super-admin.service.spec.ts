import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '@env/environment';
import { SuperAdminService } from './super-admin.service';

describe('SuperAdminService', () => {
  const base = environment.apiBaseUrl;
  let service: SuperAdminService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SuperAdminService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(SuperAdminService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists platform operators from the super-admin console', () => {
    service.list().subscribe();

    const request = http.expectOne(`${base}/super-admin/users`);
    expect(request.request.method).toBe('GET');
    request.flush({ success: true, data: [] });
  });

  it('sends no role field when creating — this endpoint makes super admins only', () => {
    // A role parameter here would be a way to mint any account on the platform from the
    // endpoint with the least surface.
    service.create({ email: 'ops@platform.test', password: null }).subscribe();

    const request = http.expectOne(`${base}/super-admin/users`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).not.toHaveProperty('roles');
    expect(request.request.body).not.toHaveProperty('role');
    request.flush({ success: true, data: { id: 'u-1', email: 'ops@platform.test' } });
  });

  it('surfaces the generated password from the create response and nowhere else', () => {
    let created: Record<string, unknown> | undefined;
    service.create({ email: 'ops@platform.test' }).subscribe((u) => (created = u as never));

    http.expectOne(`${base}/super-admin/users`).flush({
      success: true,
      data: { id: 'u-1', email: 'ops@platform.test', temporaryPassword: 'Kd9@mZq2xTvR4h' }
    });
    expect(created!['temporaryPassword']).toBe('Kd9@mZq2xTvR4h');

    // ...and the list never carries one, because it can never be read again.
    let listed: Record<string, unknown>[] | undefined;
    service.list().subscribe((u) => (listed = u as never));
    http.expectOne(`${base}/super-admin/users`).flush({
      success: true,
      data: [{ id: 'u-1', email: 'ops@platform.test', temporaryPassword: null }]
    });
    expect(listed![0]['temporaryPassword']).toBeNull();
  });
});
