import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

/** Permission-based routing: a route lists `data.roles`; the guard admits any-of.
 *  Empty or missing means "any authenticated user". */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const required = (route.data?.['roles'] as string[] | undefined) ?? [];
  if (auth.hasAnyRole(required)) return true;
  return router.createUrlTree(['/unauthorized']);
};
