export type DepartmentStatus = 'ACTIVE' | 'INACTIVE';

/** A role a department offers — mirrors backend DepartmentRoleSummary. */
export interface DepartmentRoleSummary {
  id: string;
  roleCode: string;
  roleName: string;
}

/**
 * Full representation — mirrors backend DepartmentResponse (GET /departments,
 * /departments/assignable, /departments/{id}). Carries the roles it currently offers, so
 * a user-creation form can filter its role picker to just this department's own grants.
 */
export interface Department {
  id: string;
  companyId: string;
  departmentCode: string;
  departmentName: string;
  description?: string | null;
  status: DepartmentStatus;
  roles: DepartmentRoleSummary[];
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
}

/** Body of POST /departments. `roleIds` may be empty. */
export interface CreateDepartmentRequest {
  departmentCode: string;
  departmentName: string;
  description?: string | null;
  roleIds: string[];
}

/** Body of PUT /departments/{id}. Full replacement, including the role grants. */
export interface UpdateDepartmentRequest {
  departmentName: string;
  description?: string | null;
  roleIds: string[];
  version: number;
}
