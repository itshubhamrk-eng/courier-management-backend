import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { filter, map, take, timeout, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { MenuGrantService } from '../navigation/menu-grant.service';
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
  const path = state.url.split(/[?#]/)[0];
  const decide = () => customOnly && underVisibleLeaf(nav.menu(), path) ? true : router.createUrlTree(['/unauthorized']);
  if (!customOnly) return decide();

  // A custom-role user's sidebar comes from /menu-grants/me; on a cold page load (refresh, typed
  // URL) the guard runs before it answers, so wait for it rather than bounce to /unauthorized.
  const grants = inject(MenuGrantService);
  if (grants.settled()) return decide();
  return toObservable(grants.settled).pipe(
    filter(Boolean), take(1), timeout(8000), map(decide), catchError(() => of(decide()))
  );
};

function underVisibleLeaf(nodes: NavNode[], path: string): boolean {
  return nodes.some((n) =>
    n.children?.length
      ? underVisibleLeaf(n.children, path)
      : !!n.route && (path === n.route || path.startsWith(n.route + '/')));
}
