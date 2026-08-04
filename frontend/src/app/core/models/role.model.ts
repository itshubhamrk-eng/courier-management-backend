/** Platform-wide role names carried in the JWT `roles` claim. */
export enum AppRole {
  SUPER_ADMIN = 'SUPER_ADMIN',
  PLATFORM_ADMIN = 'PLATFORM_ADMIN',
  COMPANY_ADMIN = 'COMPANY_ADMIN',
  BRANCH_MANAGER = 'BRANCH_MANAGER',
  HUB_MANAGER = 'HUB_MANAGER',
  BOOKING_OPERATOR = 'BOOKING_OPERATOR',
  DELIVERY_OPERATOR = 'DELIVERY_OPERATOR',
  /** The branch's own money desk: wallet top-ups, payments, invoices, COD reconciliation. */
  ACCOUNTS = 'ACCOUNTS',
  FINANCE_USER = 'FINANCE_USER',
  CUSTOMER_SERVICE = 'CUSTOMER_SERVICE',
  VIEWER = 'VIEWER'
}

/** Functional grouping of a company role (not "system vs custom" — that is isSystemRole). */
export type RoleType = 'ADMINISTRATION' | 'OPERATIONS' | 'FINANCE' | 'SUPPORT' | 'READ_ONLY';
export type RoleStatus = 'ACTIVE' | 'INACTIVE';

export const ROLE_TYPES: RoleType[] = ['ADMINISTRATION', 'OPERATIONS', 'FINANCE', 'SUPPORT', 'READ_ONLY'];

/**
 * List projection — mirrors backend RoleSummaryResponse (GET /roles). Carries a
 * `permissionCount`, not the full grant list; fetch RoleProfile for the codes. Shared as
 * the role-lookup shape across the app (user assignment, filters, pickers).
 */
export interface CompanyRole {
  id: string;
  companyId: string;
  roleCode: string;
  roleName: string;
  roleType: RoleType;
  isSystemRole: boolean;
  isDefault: boolean;
  status: RoleStatus;
  permissionCount: number;
  version: number;
}

/** Full representation — mirrors backend RoleResponse (GET /roles/{id}). */
export interface RoleProfile {
  id: string;
  companyId: string;
  roleCode: string;
  roleName: string;
  description?: string | null;
  roleType: RoleType;
  isSystemRole: boolean;
  isDefault: boolean;
  status: RoleStatus;
  /** Read-only here; granted by the Permission module. */
  permissions: string[];
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
}

/**
 * Body of POST /roles. `roleCode` is uppercased and spaces → underscores on save, then
 * immutable. `status` and `permissions` are not accepted (own endpoints/module).
 */
export interface CreateRoleRequest {
  roleCode: string;
  roleName: string;
  description?: string | null;
  roleType: RoleType;
  isDefault?: boolean;
}

/** Body of PUT /roles/{id}. Full replacement of the editable fields; carries `version`. */
export interface UpdateRoleRequest {
  roleName: string;
  description?: string | null;
  roleType: RoleType;
  isDefault?: boolean;
  version: number;
}

/** Advanced-filter criteria for GET /roles. All optional; merged into the page query. */
export interface RoleSearchRequest {
  status?: RoleStatus;
  roleType?: RoleType[];
  isSystemRole?: boolean;
  isDefault?: boolean;
  permissionCode?: string;
  search?: string;
}
