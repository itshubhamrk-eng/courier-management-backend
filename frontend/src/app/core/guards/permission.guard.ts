import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { PermissionService } from '../auth/permission.service';

/**
 * Fine-grained route gate. A route declares `data.permissions` and/or `data.roles`;
 * see {@link PermissionService.canAccess} for how the two combine. An unauthenticated
 * user is sent to login (preserving the target); an authenticated but unauthorised one
 * lands on /unauthorized rather than being bounced silently.
 */
export const permissionGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const permissions = inject(PermissionService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  const req = {
    roles: (route.data?.['roles'] as string[] | undefined) ?? [],
    permissions: (route.data?.['permissions'] as string[] | undefined) ?? []
  };
  if (permissions.canAccess(req)) return true;
  return router.createUrlTree(['/unauthorized']);
};
