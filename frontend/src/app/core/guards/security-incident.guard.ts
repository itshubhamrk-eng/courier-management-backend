import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { PermissionService } from '../auth/permission.service';
import { AppRole } from '../models/role.model';

/**
 * Security incidents mirror the backend gate `hasRole('PLATFORM_ADMIN') or
 * hasAuthority(<code>)`: PLATFORM_ADMIN passes on the role alone (it holds no company
 * permission codes), COMPANY_ADMIN needs the route's permission code. Nobody else is
 * admitted, even with the code, because the screen is an administrators' tool.
 * permissionGuard cannot express this: it ANDs roles with permissions.
 */
export const securityIncidentGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const permissions = inject(PermissionService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  if (permissions.hasRole(AppRole.PLATFORM_ADMIN)) return true;
  const codes = (route.data?.['permissions'] as string[] | undefined) ?? [];
  if (permissions.hasRole(AppRole.COMPANY_ADMIN) && permissions.hasAnyPermission(codes)) return true;
  return router.createUrlTree(['/unauthorized']);
};
