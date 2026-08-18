export type UserStatus = 'PENDING' | 'ACTIVE' | 'LOCKED' | 'DISABLED';
export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'UNSPECIFIED';

/** List projection — mirrors backend UserSummaryResponse (GET /users). */
export interface AppUser {
  id: string;
  companyId: string;
  employeeCode?: string;
  displayName: string;
  email: string;
  username?: string;
  mobile?: string;
  designation?: string;
  department?: string;
  branchId?: string | null;
  hubId?: string | null;
  status: UserStatus;
  isLocked: boolean;
  roleCount: number;
  createdDate?: string;
  version: number;
}

/** Full representation — mirrors backend UserResponse (GET /users/{id}). */
export interface UserProfile {
  id: string;
  companyId: string;
  employeeCode?: string;
  employeeId?: string;
  firstName?: string;
  middleName?: string;
  lastName?: string;
  displayName: string;
  email: string;
  username?: string;
  mobile?: string;
  alternateMobile?: string;
  gender?: Gender;
  dateOfBirth?: string | null;
  designation?: string;
  department?: string;
  joiningDate?: string | null;
  reportingManagerId?: string | null;
  branchId?: string | null;
  hubId?: string | null;
  profileImage?: string;
  status: UserStatus;
  isLocked: boolean;
  lastLogin?: string | null;
  failedLoginCount: number;
  remarks?: string;
  roles: string[];
  createdBy?: string;
  createdDate?: string;
  updatedBy?: string;
  updatedDate?: string;
  version: number;
  /** Only on a creation response: true when the password was auto-generated (PENDING). */
  passwordGenerated?: boolean;
}

/** Body of POST /users. `password` omitted → PENDING account; `roleIds` empty → default role. */
export interface CreateUserRequest {
  employeeCode?: string | null;
  employeeId?: string | null;
  firstName: string;
  middleName?: string | null;
  lastName?: string | null;
  displayName?: string | null;
  email: string;
  username?: string | null;
  mobile?: string | null;
  alternateMobile?: string | null;
  password?: string | null;
  gender?: Gender | null;
  dateOfBirth?: string | null;
  designation?: string | null;
  department?: string | null;
  joiningDate?: string | null;
  reportingManagerId?: string | null;
  branchId?: string | null;
  hubId?: string | null;
  profileImage?: string | null;
  remarks?: string | null;
  roleIds?: string[];
}

/** Body of PUT /users/{id}. Full replacement of the editable profile; carries `version`. */
export interface UpdateUserRequest {
  firstName: string;
  middleName?: string | null;
  lastName?: string | null;
  displayName?: string | null;
  mobile?: string | null;
  alternateMobile?: string | null;
  gender?: Gender | null;
  dateOfBirth?: string | null;
  designation?: string | null;
  department?: string | null;
  joiningDate?: string | null;
  reportingManagerId?: string | null;
  branchId?: string | null;
  hubId?: string | null;
  profileImage?: string | null;
  remarks?: string | null;
  version: number;
}

/** Advanced-filter criteria for GET /users. All optional; merged into the page query. */
export interface UserSearchRequest {
  status?: UserStatus[];
  locked?: boolean;
  branchId?: string;
  hubId?: string;
  department?: string;
  designation?: string;
  roleCode?: string;
  joinedFrom?: string;
  joinedTo?: string;
  search?: string;
}

/** The /auth/me projection used to hydrate the session. */
export interface CurrentUser {
  userId: string;
  companyId: string | null;
  email: string;
  displayName: string;
  roles: string[];
  /** Effective permission codes; empty until the backend authorises on permissions. */
  permissions?: string[];
  branchId?: string | null;
  hubId?: string | null;
  companyName?: string | null;
  companyLogo?: string | null;
  /** Set only while a SUPER_ADMIN is impersonating this company's admin — see AuthService.impersonateCompany. */
  impersonatedBy?: { userId?: string; email: string } | null;
}
