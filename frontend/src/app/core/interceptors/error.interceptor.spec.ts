import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { environment } from '@env/environment';
import { AuthService } from '../auth/auth.service';
import { TokenService } from '../auth/token.service';
import { SessionService } from '../auth/session.service';
import { NotificationService } from '../services/notification.service';
import { errorInterceptor } from './error.interceptor';

/**
 * The backend treats a repeated, already-rotated refresh token (outside a short grace from
 * the same IP) as theft and bans the user. Parallel 401s must therefore share ONE refresh.
 */
describe('errorInterceptor single-flight refresh', () => {
  const base = environment.apiBaseUrl;
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let tokenState: { access: string; refresh: string };
  const auth = { clearSession: vi.fn(), exitImpersonation: vi.fn() };
  const notify = { error: vi.fn() };
  const router = { navigate: vi.fn() };
  const session = { syncExpiry: vi.fn() };

  beforeEach(() => {
    tokenState = { access: 'old', refresh: 'r1' };
    auth.clearSession.mockReset();
    auth.clearSession.mockImplementation(() => { tokenState = { access: '', refresh: '' }; });
    notify.error.mockReset();
    router.navigate.mockReset();
    session.syncExpiry.mockReset();
    const tokens = {
      get accessToken() { return tokenState.access; },
      get refreshToken() { return tokenState.refresh; },
      isImpersonating: false,
      setTokens: (a: string, r: string) => { tokenState = { access: a, refresh: r }; }
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: TokenService, useValue: tokens },
        { provide: SessionService, useValue: session },
        { provide: Router, useValue: router },
        { provide: NotificationService, useValue: notify }
      ]
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
  });

  afterEach(() => ctrl.verify());

  const bearer = (token: string) => ({ headers: new HttpHeaders({ Authorization: `Bearer ${token}` }) });

  it('N parallel 401s trigger exactly one refresh and every request is retried with the new token', () => {
    const results: unknown[] = [];
    for (const path of ['/a', '/b', '/c']) {
      http.get(`${base}${path}`, bearer('old')).subscribe((r) => results.push(r));
    }
    for (const path of ['/a', '/b', '/c']) {
      ctrl.expectOne(`${base}${path}`).flush({}, { status: 401, statusText: 'Unauthorized' });
    }

    const refresh = ctrl.expectOne(`${base}/auth/refresh`);
    expect(refresh.request.body).toEqual({ refreshToken: 'r1' });
    refresh.flush({ success: true, data: { accessToken: 'new', refreshToken: 'r2' } });

    for (const path of ['/a', '/b', '/c']) {
      const retry = ctrl.expectOne(`${base}${path}`);
      expect(retry.request.headers.get('Authorization')).toBe('Bearer new');
      retry.flush({ data: path });
    }
    expect(results.length).toBe(3);
    expect(session.syncExpiry).toHaveBeenCalledTimes(1);
    expect(auth.clearSession).not.toHaveBeenCalled();
  });

  it('a refresh failure signs out once and no waiter errors out', () => {
    const errors: unknown[] = [];
    let completed = 0;
    for (const path of ['/a', '/b']) {
      http.get(`${base}${path}`, bearer('old')).subscribe({ error: (e) => errors.push(e), complete: () => completed++ });
    }
    for (const path of ['/a', '/b']) {
      ctrl.expectOne(`${base}${path}`).flush({}, { status: 401, statusText: 'Unauthorized' });
    }
    ctrl.expectOne(`${base}/auth/refresh`).flush({ message: 'bad' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.clearSession).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    expect(errors).toEqual([]);
    expect(completed).toBe(2);
  });

  it('a late 401 from a request sent with the old token replays with the new one and does not refresh again', () => {
    http.get(`${base}/a`, bearer('old')).subscribe();
    http.get(`${base}/b`, bearer('old')).subscribe();
    ctrl.expectOne(`${base}/a`).flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne(`${base}/auth/refresh`).flush({ data: { accessToken: 'new', refreshToken: 'r2' } });
    ctrl.expectOne(`${base}/a`).flush({ data: 1 });

    ctrl.expectOne(`${base}/b`).flush({}, { status: 401, statusText: 'Unauthorized' });
    const retry = ctrl.expectOne(`${base}/b`);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new');
    retry.flush({ data: 2 });
    ctrl.expectNone(`${base}/auth/refresh`);
  });

  it('refresh failing because another tab rotated the stored pair adopts it: no logout, replay with its access token', () => {
    http.get(`${base}/a`, bearer('old')).subscribe();
    http.get(`${base}/b`, bearer('old')).subscribe();
    ctrl.expectOne(`${base}/a`).flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne(`${base}/b`).flush({}, { status: 401, statusText: 'Unauthorized' });
    const refresh = ctrl.expectOne(`${base}/auth/refresh`);
    // The winning tab stores its pair in shared storage before our rejection arrives.
    tokenState = { access: 'tab2-access', refresh: 'tab2-refresh' };
    refresh.flush({ message: 'concurrent' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.clearSession).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
    for (const path of ['/a', '/b']) {
      const retry = ctrl.expectOne(`${base}${path}`);
      expect(retry.request.headers.get('Authorization')).toBe('Bearer tab2-access');
      retry.flush({ data: path });
    }
    ctrl.expectNone(`${base}/auth/refresh`);
  });

  it('a genuine refresh failure (stored refresh token unchanged) signs out exactly once', () => {
    http.get(`${base}/a`, bearer('old')).subscribe();
    http.get(`${base}/b`, bearer('old')).subscribe();
    ctrl.expectOne(`${base}/a`).flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne(`${base}/b`).flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne(`${base}/auth/refresh`).flush({ message: 'bad' }, { status: 401, statusText: 'Unauthorized' });
    expect(auth.clearSession).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledTimes(1);
  });

  describe('errors on a replayed request', () => {
    function replayWith(status: number, body: object = {}) {
      http.get(`${base}/a`, bearer('old')).subscribe({ error: () => undefined });
      ctrl.expectOne(`${base}/a`).flush({}, { status: 401, statusText: 'Unauthorized' });
      ctrl.expectOne(`${base}/auth/refresh`).flush({ data: { accessToken: 'new', refreshToken: 'r2' } });
      ctrl.expectOne(`${base}/a`).flush(body, { status, statusText: 'x' });
    }

    it('a 500 shows the usual toast and does not sign out', () => {
      replayWith(500, { message: 'boom' });
      expect(notify.error).toHaveBeenCalledWith('boom');
      expect(auth.clearSession).not.toHaveBeenCalled();
    });

    it('a 403 gets the normal permission toast and does not sign out', () => {
      replayWith(403);
      expect(notify.error).toHaveBeenCalledWith('You do not have permission to perform this action.');
      expect(auth.clearSession).not.toHaveBeenCalled();
    });

    it('a 401 signs out exactly once with no second refresh call', () => {
      replayWith(401);
      expect(auth.clearSession).toHaveBeenCalledTimes(1);
      expect(router.navigate).toHaveBeenCalledTimes(1);
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
      ctrl.expectNone(`${base}/auth/refresh`);
    });

    it('two replayed requests both getting 401 still navigate to login once', () => {
      http.get(`${base}/a`, bearer('old')).subscribe({ error: () => undefined });
      http.get(`${base}/b`, bearer('old')).subscribe({ error: () => undefined });
      ctrl.expectOne(`${base}/a`).flush({}, { status: 401, statusText: 'Unauthorized' });
      ctrl.expectOne(`${base}/b`).flush({}, { status: 401, statusText: 'Unauthorized' });
      ctrl.expectOne(`${base}/auth/refresh`).flush({ data: { accessToken: 'new', refreshToken: 'r2' } });
      ctrl.expectOne(`${base}/a`).flush({}, { status: 401, statusText: 'Unauthorized' });
      ctrl.expectOne(`${base}/b`).flush({}, { status: 401, statusText: 'Unauthorized' });
      expect(router.navigate).toHaveBeenCalledTimes(1);
      expect(auth.clearSession).toHaveBeenCalledTimes(1);
    });
  });

  it('a later 401 after a completed refresh starts a fresh refresh with the new refresh token', () => {
    http.get(`${base}/a`, bearer('old')).subscribe();
    ctrl.expectOne(`${base}/a`).flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne(`${base}/auth/refresh`).flush({ data: { accessToken: 'new', refreshToken: 'r2' } });
    ctrl.expectOne(`${base}/a`).flush({ data: 1 });

    http.get(`${base}/b`, bearer('new')).subscribe();
    ctrl.expectOne(`${base}/b`).flush({}, { status: 401, statusText: 'Unauthorized' });
    const second = ctrl.expectOne(`${base}/auth/refresh`);
    expect(second.request.body).toEqual({ refreshToken: 'r2' });
    second.flush({ data: { accessToken: 'newer', refreshToken: 'r3' } });
    ctrl.expectOne(`${base}/b`).flush({ data: 2 });
  });

  it('leaves a geo-restricted login to the login page instead of a "no permission" toast', () => {
    let err: unknown;
    http.post(`${base}/auth/login`, {}).subscribe({ error: (e) => (err = e) });
    ctrl.expectOne(`${base}/auth/login`).flush(
      { errorCode: 'GEO_RESTRICTED', message: 'Sign-in is not available from your location.' },
      { status: 403, statusText: 'Forbidden' });
    expect(err).toBeTruthy();
    expect(notify.error).not.toHaveBeenCalled();
  });

  it('a failure of the retried request itself does not sign the user out', () => {
    let err: unknown;
    http.get(`${base}/a`, bearer('old')).subscribe({ error: (e) => (err = e) });
    ctrl.expectOne(`${base}/a`).flush({}, { status: 401, statusText: 'Unauthorized' });
    ctrl.expectOne(`${base}/auth/refresh`).flush({ data: { accessToken: 'new', refreshToken: 'r2' } });
    ctrl.expectOne(`${base}/a`).flush({ message: 'nope' }, { status: 404, statusText: 'Not Found' });
    expect(err).toBeTruthy();
    expect(auth.clearSession).not.toHaveBeenCalled();
  });
});
