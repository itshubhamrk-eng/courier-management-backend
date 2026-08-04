import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenService } from '../auth/token.service';
import { API } from '../config/api-endpoints';

/** Attaches the bearer token and, for an impersonating platform admin, X-Company-ID.
 *  The login/refresh calls are left unauthenticated. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokens = inject(TokenService);
  const isAuthCall = req.url.includes(API.auth.login) || req.url.includes(API.auth.refresh);

  let headers = req.headers;
  const access = tokens.accessToken;
  if (access && !isAuthCall) headers = headers.set('Authorization', `Bearer ${access}`);
  const company = tokens.companyId;
  if (company && !isAuthCall) headers = headers.set('X-Company-ID', company);

  return next(req.clone({ headers }));
};
