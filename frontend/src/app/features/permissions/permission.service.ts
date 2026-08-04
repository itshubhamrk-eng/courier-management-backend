import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  Permission, RolePermissionResult, PermissionAssignmentRequest
} from '@core/models/permission.model';
import { Page, PageQuery } from '@core/models/page.model';

/**
 * Permission Management — talks to the backend permission catalogue and the
 * role→permission grants. Mirrors the endpoints one-to-one; no mock data.
 *
 * The catalogue (`/permissions`) is platform-level and read-only to a company; the
 * grants (`/roles/{roleId}/permissions`) are company-owned and bulk by design — a matrix
 * submits the whole set in one transaction so a role is never left half-configured.
 */
@Injectable({ providedIn: 'root' })
export class PermissionService {
  private readonly api = inject(ApiService);

  // ---- catalogue reads ------------------------------------------------------
  /** Paged, sorted, filtered, searchable. `size` capped at 200 by the backend. */
  list(query: PageQuery) { return this.api.page<Permission>(API.permissions, query); }
  get(id: string) { return this.api.get<Permission>(`${API.permissions}/${id}`); }
  /** Every ACTIVE permission in display order, unpaged — the source for a matrix screen. */
  grantable() { return this.api.get<Permission[]>(`${API.permissions}/grantable`); }

  // ---- role grants ----------------------------------------------------------
  /** The catalogue rows a role currently holds. */
  rolePermissions(roleId: string) {
    return this.api.get<Permission[]>(`${API.roles}/${roleId}/permissions`);
  }

  /**
   * Bulk assign. `replaceExisting=true` makes the role hold exactly the supplied set.
   * The response reports granted / revoked / skipped / rejected — surface `rejected`.
   */
  assign(roleId: string, body: PermissionAssignmentRequest) {
    return this.api.post<RolePermissionResult>(`${API.roles}/${roleId}/permissions`, body);
  }

  /** Revoke one permission from a role — idempotent on the backend. */
  revoke(roleId: string, permissionId: string) {
    return this.api.delete<void>(`${API.roles}/${roleId}/permissions/${permissionId}`);
  }
}
