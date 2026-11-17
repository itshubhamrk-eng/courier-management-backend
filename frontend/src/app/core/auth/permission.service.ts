import { Injectable, computed, inject } from '@angular/core';
import { AuthService } from './auth.service';
import { AppRole } from '../models/role.model';

const BUILT_IN_ROLES = new Set<string>(Object.values(AppRole));

/** What a guard or template asks about: any-of roles and/or any-of permission codes. */
export interface AccessRequirement {
  roles?: string[];
  permissions?: string[];
}

/**
 * Authorisation queries for the UI. This gates *what the user sees*; the backend
 * remains the real enforcement point. Roles come from the JWT today; permission codes
 * are consumed once the backend authorises on permissions — until then `permissions()`
 * is empty and role checks carry access, so nothing here fabricates rights.
 */
@Injectable({ providedIn: 'root' })
export class PermissionService {
  private readonly auth = inject(AuthService);

  private readonly roleSet = computed(() => new Set(this.auth.roles()));
  private readonly permissionSet = computed(() => new Set(this.auth.permissions()));

  hasRole(role: string): boolean { return this.roleSet().has(role); }
  hasAnyRole(roles: string[]): boolean {
    return roles.length === 0 || roles.some((r) => this.roleSet().has(r));
  }

  hasPermission(code: string): boolean { return this.permissionSet().has(code); }
  hasAnyPermission(codes: string[]): boolean {
    return codes.length === 0 || codes.some((c) => this.permissionSet().has(c));
  }
  hasAllPermissions(codes: string[]): boolean {
    return codes.every((c) => this.permissionSet().has(c));
  }

  /**
   * Admits when there are no requirements. When only one of roles/permissions is given,
   * that one alone decides — keeps role-only routes (nothing granular to check) and
   * permission-only checks both working standalone. When both are given, both must pass:
   * role narrows to who could ever have this, permission is the per-user grant (role
   * default, overridable per user) that decides whether *this* user still does — a role
   * match alone must not resurrect a menu/action a user's permission was revoked from.
   */
  canAccess(req: AccessRequirement): boolean {
    const roles = req.roles ?? [];
    const perms = req.permissions ?? [];
    if (roles.length === 0 && perms.length === 0) return true;
    // A user whose roles are all company-defined custom roles (none of the built-in ones the
    // `roles` bridge lists) is governed by their permission grants alone: the bridge only
    // narrows built-in roles, and would otherwise hide every menu a custom-role user was granted.
    const customOnly = perms.length > 0 && this.auth.roles().length > 0
      && this.auth.roles().every((r) => !BUILT_IN_ROLES.has(r));
    const roleOk = roles.length === 0 || customOnly || this.hasAnyRole(roles);
    const permOk = perms.length === 0 || this.hasAnyPermission(perms);
    return roleOk && permOk;
  }
}
