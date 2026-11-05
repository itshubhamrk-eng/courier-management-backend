import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, of, shareReplay, tap, throwError } from 'rxjs';
import { ApiService } from '../services/api.service';
import { API } from '../config/api-endpoints';
import { LoginResponse } from '../models/auth.model';
import { AuthService } from './auth.service';
import { TokenService } from './token.service';
import { SessionService } from './session.service';

/**
 * Single-flight token rotation. The backend treats a refresh token that was already rotated
 * (outside a short grace window) as theft and bans the user, so N parallel 401s must share
 * ONE POST /auth/refresh instead of each replaying the same refresh token.
 *
 * Everyone who asks while a refresh is in flight gets the same Observable. On success the new
 * pair is stored once; on a genuine failure the session is cleared and the user sent to /login
 * once, and every waiter receives the same error.
 */
@Injectable({ providedIn: 'root' })
export class TokenRefreshService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly tokens = inject(TokenService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  private inFlight: Observable<LoginResponse> | null = null;

  refresh(): Observable<LoginResponse> {
    if (!this.inFlight) {
      const sent = this.tokens.refreshToken;
      this.inFlight = this.api
        .post<LoginResponse>(API.auth.refresh, { refreshToken: sent })
        .pipe(
          tap((res) => {
            this.tokens.setTokens(res.accessToken, res.refreshToken);
            this.session.syncExpiry();
          }),
          catchError((err) => {
            // Tokens live in shared storage. If another tab rotated the pair while this
            // refresh was failing (the backend rejects the concurrent loser), the stored
            // refresh token is no longer the one we sent: that tab won, so adopt its pair
            // instead of wiping it and signing the user out everywhere.
            const stored = this.tokens.refreshToken;
            const access = this.tokens.accessToken;
            if (stored && access && stored !== sent) {
              this.session.syncExpiry();
              return of({ accessToken: access, refreshToken: stored } as LoginResponse);
            }
            this.signOut();
            return throwError(() => err);
          }),
          finalize(() => { this.inFlight = null; }),
          shareReplay({ bufferSize: 1, refCount: false })
        );
    }
    return this.inFlight;
  }

  /**
   * Clears the session and goes to /login, at most once per session: a second caller finds
   * the tokens already gone and does nothing (no double navigation).
   */
  signOut(): void {
    if (!this.tokens.accessToken && !this.tokens.refreshToken) return;
    this.auth.clearSession();
    this.router.navigate(['/login']);
  }
}
