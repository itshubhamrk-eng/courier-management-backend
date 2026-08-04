import { HttpInterceptorFn } from '@angular/common/http';

/** Sends X-Request-Id so a UI action maps to a backend log line (ARCHITECTURE §9). */
export const requestIdInterceptor: HttpInterceptorFn = (req, next) => {
  const id = (crypto as Crypto & { randomUUID?: () => string }).randomUUID?.()
    ?? Math.random().toString(36).slice(2);
  return next(req.clone({ headers: req.headers.set('X-Request-Id', id) }));
};
