import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { NavigationService } from '../navigation/navigation.service';
import { NavNode } from '../navigation/navigation.model';
import { AppRole } from '../models/role.model';

const BUILT_IN_ROLES = new Set<string>(Object.values(AppRole));

/** Permission-based routing: a route lists `data.roles`; the guard admits any-of.
 *  Empty or missing means "any authenticated user".
 *
 *  A user whose roles are all company-defined custom roles can never match a built-in role
 *  name, so for them the route is admitted when it sits under a menu leaf they can see —
 *  that leaf already passed the permission check, so their grants decide, not role names. */
export const roleGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const nav = inject(NavigationService);
  const required = (route.data?.['roles'] as string[] | undefined) ?? [];
  if (auth.hasAnyRole(required)) return true;

  const roles = auth.roles();
  const customOnly = roles.length > 0 && roles.every((r) => !BUILT_IN_ROLES.has(r));
  if (customOnly && underVisibleLeaf(nav.menu(), state.url.split(/[?#]/)[0])) return true;

  return router.createUrlTree(['/unauthorized']);
};

function underVisibleLeaf(nodes: NavNode[], path: string): boolean {
  return nodes.some((n) =>
    n.children?.length
      ? underVisibleLeaf(n.children, path)
      : !!n.route && (path === n.route || path.startsWith(n.route + '/')));
}
