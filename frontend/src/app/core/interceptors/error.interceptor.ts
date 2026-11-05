import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { TokenService } from '../auth/token.service';
import { TokenRefreshService } from '../auth/token-refresh.service';
import { SILENT_ERRORS } from '../services/api.service';
import { NotificationService } from '../services/notification.service';
import { API } from '../config/api-endpoints';

/**
 * Maps the ApiResponse error envelope to a toast, and — on a 401 with a usable refresh
 * token — rotates the pair (one shared refresh for all parallel 401s, see TokenRefreshService)
 * and replays the request. A failed refresh signs out.
 * Validation (400/422) messages are left for the form to render field-by-field.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const auth = inject(AuthService);
  const tokens = inject(TokenService);
  const refresher = inject(TokenRefreshService);
  const notify = inject(NotificationService);

  /** Maps a failed (non-refreshable) response to its toast / sign-out. Never refreshes. */
  const handle = (failed: HttpRequest<unknown>, err: HttpErrorResponse) => {
    const envelope = err.error ?? {};
    const isAuthCall = failed.url.includes(API.auth.login) || failed.url.includes(API.auth.refresh);
    const silent = failed.context.get(SILENT_ERRORS);

    // An impersonation session carries no refresh token by design (it hard-expires,
    // see TokenService.beginImpersonation) — so its 401 never reaches the refresh
    // branch. Falling back to the stashed real session beats dumping a
    // SUPER_ADMIN on the login page mid-navigation.
    if (err.status === 401 && !isAuthCall && tokens.isImpersonating) {
      auth.exitImpersonation();
      notify.error('Your impersonation session expired. Returned to your own session.');
      router.navigate(['/dashboard']);
    } else if (err.status === 401 && !isAuthCall) {
      refresher.signOut();
    } else if (isAuthCall && (envelope.errorCode === 'GEO_RESTRICTED' || envelope.errorCode === 'RATE_LIMIT_EXCEEDED')) {
      // The login page renders these inline; a "no permission" toast would be misleading.
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
  };

  /** Replays a request with a fresh token. Its errors get the normal mapping but never
   *  re-enter the refresh branch: a 401 here means the new token is also rejected -> sign out. */
  const replay = (access: string) => {
    const retried = req.clone({ headers: req.headers.set('Authorization', `Bearer ${access}`) });
    return next(retried).pipe(catchError((e: HttpErrorResponse) => handle(retried, e)));
  };

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const envelope = err.error ?? {};
      // "Request validation failed" names no field; the real `field: message` pairs travel in
      // errors[]. Fold them into message so every screen that shows err.error.message gets them.
      const fieldErrors: { field: string; message: string }[] = Array.isArray(envelope.errors) ? envelope.errors : [];
      if (fieldErrors.length && typeof envelope.message === 'string') {
        envelope.message = fieldErrors.map((e) => `${e.field}: ${e.message}`).join('; ');
      }
      const isAuthCall = req.url.includes(API.auth.login) || req.url.includes(API.auth.refresh);

      if (err.status === 401 && !isAuthCall && tokens.refreshToken) {
        // Another request already rotated the pair after this one was sent: just replay with
        // the current token. Refreshing again would present an already-rotated refresh token.
        const current = tokens.accessToken;
        if (current && req.headers.get('Authorization') !== `Bearer ${current}`) {
          return replay(current);
        }
        // The shared refresh signs out (once) on a genuine failure; waiters then complete silently.
        return refresher.refresh().pipe(
          catchError(() => EMPTY),
          switchMap((res) => replay(res.accessToken))
        );
      }
      return handle(req, err);
    })
  );
};
