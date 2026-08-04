/**
 * Permission Management models — mirror the backend `permissions` + `role_permissions`
 * catalogue one-to-one (see MEMORY/modules/permission.md). No mock shapes: every field
 * here is returned by, or accepted by, an actual endpoint.
 */

export type PermissionStatus = 'ACTIVE' | 'INACTIVE';

/** A permission action — the verb half of a `MODULE_ACTION` code. 15 platform actions. */
export type PermissionAction =
  | 'CREATE' | 'READ' | 'UPDATE' | 'DELETE' | 'SEARCH' | 'EXPORT' | 'IMPORT'
  | 'APPROVE' | 'REJECT' | 'PRINT' | 'UPLOAD' | 'DOWNLOAD' | 'ASSIGN'
  | 'ACTIVATE' | 'DEACTIVATE';

export const PERMISSION_ACTIONS: PermissionAction[] = [
  'CREATE', 'READ', 'UPDATE', 'DELETE', 'SEARCH', 'EXPORT', 'IMPORT',
  'APPROVE', 'REJECT', 'PRINT', 'UPLOAD', 'DOWNLOAD', 'ASSIGN', 'ACTIVATE', 'DEACTIVATE'
];

/** The 28 platform permission modules — the noun half of a code. */
export const PERMISSION_MODULES: string[] = [
  'AUTH', 'COMPANY', 'USER', 'ROLE', 'PERMISSION', 'BRANCH', 'HUB', 'CUSTOMER',
  'ADDRESS', 'PINCODE', 'RATE_MASTER', 'ROUTE_MASTER', 'SHIPMENT', 'TRACKING',
  'MANIFEST', 'PICKUP', 'DELIVERY', 'DRIVER', 'VEHICLE', 'VENDOR', 'WALLET',
  'PAYMENT', 'INVOICE', 'REPORT', 'DASHBOARD', 'SETTINGS', 'NOTIFICATION', 'AUDIT'
];

/**
 * A permission as the catalogue exposes it — mirrors backend `PermissionResponse`.
 * `permissionCode` is derived `MODULE_ACTION`, immutable. Audit fields present on the
 * detail read, absent from the list projection (both share this shape safely — optional).
 */
export interface Permission {
  id: string;
  permissionCode: string;
  permissionName: string;
  module: string;
  resource: string;
  action: PermissionAction;
  description?: string | null;
  isSystemPermission: boolean;
  status: PermissionStatus;
  displayOrder: number;
  /** Subscription feature required to grant it, or null when unconditional. */
  requiredFeatureFlag?: string | null;
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
}

/**
 * A module and its permissions — the client-side grouping a tree/matrix is built from.
 * Not a backend shape: assembled from a flat `Permission[]` by {@link groupByModule}.
 */
export interface PermissionGroup {
  module: string;
  permissions: Permission[];
}

/**
 * A role's current grants, mirrors backend `RolePermissionResponse` — the result of an
 * assignment. The four lists are reported separately because "it worked" is not the whole
 * truth: `rejected` (plan-gated or inactive) is the field the UI must surface.
 */
export interface RolePermissionResult {
  roleId: string;
  roleCode: string;
  /** Newly added. */
  granted: string[];
  /** Removed, when replacing the set. */
  revoked: string[];
  /** Already held, so not re-added. */
  skipped: string[];
  /** Refused: inactive, or outside the company's subscription plan. */
  rejected: string[];
  /** Everything the role holds after this call. */
  effectivePermissions: string[];
}

/**
 * Body of `POST /roles/{roleId}/permissions` — mirrors backend `RolePermissionRequest`.
 * Codes, not ids (a matrix knows `SHIPMENT_CREATE`; codes are stable across environments).
 * `replaceExisting=true` makes the role hold exactly this set — what a "save" button means.
 */
export interface PermissionAssignmentRequest {
  permissionCodes: string[];
  replaceExisting: boolean;
}

/** Advanced-filter criteria for `GET /permissions`. All optional; merged into the query. */
export interface PermissionSearchRequest {
  module?: string[];
  action?: PermissionAction[];
  status?: PermissionStatus;
  isSystemPermission?: boolean;
  resource?: string;
  planGatedOnly?: boolean;
  search?: string;
}

/** Group a flat permission list into modules, each sorted by displayOrder, module-ordered. */
export function groupByModule(permissions: Permission[]): PermissionGroup[] {
  const order = new Map(PERMISSION_MODULES.map((m, i) => [m, i]));
  const byModule = new Map<string, Permission[]>();
  for (const p of permissions) {
    const list = byModule.get(p.module) ?? [];
    list.push(p);
    byModule.set(p.module, list);
  }
  return [...byModule.entries()]
    .map(([module, perms]) => ({
      module,
      permissions: perms.sort((a, b) => a.displayOrder - b.displayOrder)
    }))
    .sort((a, b) => (order.get(a.module) ?? 999) - (order.get(b.module) ?? 999));
}

/** Human label for a MODULE / ACTION / STATUS token: `RATE_MASTER` → `Rate Master`. */
export function prettyToken(v?: string | null): string {
  return v ? v.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase()) : '—';
}
