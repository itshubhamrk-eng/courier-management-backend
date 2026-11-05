import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, tap, throwError } from 'rxjs';
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
 * pair is stored once; on failure the session is cleared and the user sent to /login once,
 * and every waiter receives the same error.
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
      this.inFlight = this.api
        .post<LoginResponse>(API.auth.refresh, { refreshToken: this.tokens.refreshToken })
        .pipe(
          tap((res) => {
            this.tokens.setTokens(res.accessToken, res.refreshToken);
            this.session.syncExpiry();
          }),
          catchError((err) => {
            this.auth.clearSession();
            this.router.navigate(['/login']);
            return throwError(() => err);
          }),
          finalize(() => { this.inFlight = null; }),
          shareReplay({ bufferSize: 1, refCount: false })
        );
    }
    return this.inFlight;
  }
}
