import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { TokenService } from '../auth/token.service';
import { SessionService } from '../auth/session.service';
import { ApiService, SILENT_ERRORS } from '../services/api.service';
import { NotificationService } from '../services/notification.service';
import { API } from '../config/api-endpoints';
import { LoginResponse } from '../models/auth.model';

/**
 * Maps the ApiResponse error envelope to a toast, and — on a 401 with a usable refresh
 * token — rotates the pair once and replays the request. A failed refresh signs out.
 * Validation (400/422) messages are left for the form to render field-by-field.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const auth = inject(AuthService);
  const tokens = inject(TokenService);
  const session = inject(SessionService);
  const api = inject(ApiService);
  const notify = inject(NotificationService);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const envelope = err.error ?? {};
      const isAuthCall = req.url.includes(API.auth.login) || req.url.includes(API.auth.refresh);

      if (err.status === 401 && !isAuthCall && tokens.refreshToken) {
        return api.post<LoginResponse>(API.auth.refresh, { refreshToken: tokens.refreshToken }).pipe(
          switchMap((res) => {
            tokens.setTokens(res.accessToken, res.refreshToken);
            session.syncExpiry();
            return next(req.clone({ headers: req.headers.set('Authorization', `Bearer ${res.accessToken}`) }));
          }),
          catchError(() => {
            auth.clearSession();
            router.navigate(['/session-expired']);
            return EMPTY;
          })
        );
      }

      const silent = req.context.get(SILENT_ERRORS);

      // An impersonation session carries no refresh token by design (it hard-expires,
      // see TokenService.beginImpersonation) — so its 401 never reaches the refresh
      // branch above. Falling back to the stashed real session beats dumping a
      // SUPER_ADMIN on the generic /session-expired page mid-navigation.
      if (err.status === 401 && !isAuthCall && tokens.isImpersonating) {
        auth.exitImpersonation();
        notify.error('Your impersonation session expired. Returned to your own session.');
        router.navigate(['/dashboard']);
      } else if (err.status === 401 && !isAuthCall) {
        auth.clearSession();
        router.navigate(['/session-expired']);
      } else if (silent) {
        // Expected-to-sometimes-fail lookup (e.g. resolving a user id that may belong
        // to another tenant/platform tier) — the caller's own catchError handles it.
      } else if (err.status === 403) {
        notify.error('You do not have permission to perform this action.');
      } else if (err.status >= 500) {
        notify.error(envelope.message ?? 'Something went wrong. Please try again.');
      } else if (err.status !== 400 && err.status !== 422) {
        notify.error(envelope.message ?? `Request failed (${err.status}).`);
      }
      return throwError(() => err);
    })
  );
};
