import { Injectable, computed, inject } from '@angular/core';
import { AuthService } from './auth.service';

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
   * Admits when there are no requirements, or the user satisfies *any* required role or
   * permission. OR across the two so a role-only route keeps working before permissions
   * are wired, and a permission-only route works once they are.
   */
  canAccess(req: AccessRequirement): boolean {
    const roles = req.roles ?? [];
    const perms = req.permissions ?? [];
    if (roles.length === 0 && perms.length === 0) return true;
    const roleOk = roles.length > 0 && this.hasAnyRole(roles);
    const permOk = perms.length > 0 && this.hasAnyPermission(perms);
    return roleOk || permOk;
  }
}
