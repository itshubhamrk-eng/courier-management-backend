import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, map, of, switchMap, throwError } from 'rxjs';
import { LoginResponse, PlatformSsoConfig } from '../models/auth.model';
import { API } from '../config/api-endpoints';
import { ApiService } from '../services/api.service';
import { codeChallenge, randomString } from '../utils/pkce.util';
import { storage } from '../utils/storage.util';
import { AuthService } from './auth.service';

const PENDING = 'occpl.sso.pending';        // sessionStorage: { state, verifier, company }
const LAST_COMPANY = 'occpl.sso.company';    // localStorage: the platform company used last time

interface Pending { state: string; verifier: string; company: string; }

/**
 * "Sign in with OCCPL": a standard authorization-code + PKCE round trip to the platform's Keycloak, then one call to
 * our own server, which verifies the platform token, asks the platform whether this person may use the company, and
 * answers with the normal courier session. The platform token is used once and never stored.
 */
@Injectable({ providedIn: 'root' })
export class PlatformSsoService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  /** Where the platform sign-in is configured; null when the server has it switched off (404) or is unreachable. */
  config(): Observable<PlatformSsoConfig | null> {
    return this.api.get<PlatformSsoConfig>(API.auth.platformConfig).pipe(catchError(() => of(null)));
  }

  /** The platform company used on this browser last time, so the login page can offer a shortcut. */
  get lastCompany(): string | null { return storage.get(LAST_COMPANY); }

  forgetLastCompany(): void { storage.remove(LAST_COMPANY); }

  /** The page the platform sends people back to (the same `/sso` route that starts the flow). */
  get redirectUri(): string { return `${window.location.origin}/sso`; }

  /** Sends the browser to the platform to sign in. Never returns normally. */
  async begin(config: PlatformSsoConfig, platformCompanyId: string): Promise<void> {
    const pending: Pending = { state: randomString(), verifier: randomString(48), company: platformCompanyId };
    sessionStorage.setItem(PENDING, JSON.stringify(pending));
    const params = new URLSearchParams({
      client_id: config.clientId,
      response_type: 'code',
      scope: 'openid email profile',
      redirect_uri: this.redirectUri,
      state: pending.state,
      code_challenge: await codeChallenge(pending.verifier),
      code_challenge_method: 'S256'
    });
    window.location.assign(`${config.authorizationEndpoint}?${params.toString()}`);
  }

  /** Finishes the round trip: checks `state`, trades the code for the platform token, then signs in. */
  complete(config: PlatformSsoConfig, code: string, state: string): Observable<LoginResponse> {
    const raw = sessionStorage.getItem(PENDING);
    sessionStorage.removeItem(PENDING);
    const pending: Pending | null = raw ? JSON.parse(raw) : null;
    if (!pending || pending.state !== state) {
      return throwError(() => new Error('This sign-in link is no longer valid. Start again from the platform.'));
    }
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: config.clientId,
      code,
      redirect_uri: this.redirectUri,
      code_verifier: pending.verifier
    });
    // Plain fetch: our own HTTP interceptors must not attach the courier token to a call to another host.
    return from(fetch(config.tokenEndpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString()
    }).then(async (r) => {
      if (!r.ok) throw new Error('The platform did not accept the sign-in. Start again from the platform.');
      return (await r.json()) as { access_token?: string };
    })).pipe(
      map((t) => {
        if (!t.access_token) throw new Error('The platform did not return a sign-in.');
        return t.access_token;
      }),
      switchMap((token) => this.auth.loginWithPlatform(token, pending.company)),
      map((session) => {
        storage.set(LAST_COMPANY, pending.company);
        return session;
      })
    );
  }
}
