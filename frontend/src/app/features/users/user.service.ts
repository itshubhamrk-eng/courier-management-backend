import { Injectable, inject } from '@angular/core';
import { of } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  AppUser, UserProfile, CreateUserRequest, UpdateUserRequest
} from '@core/models/user.model';
import { CompanyRole } from '@core/models/role.model';
import { Branch } from '@core/models/branch.model';
import { Page, PageQuery } from '@core/models/page.model';

/** A minimal option for placement/manager/role dropdowns. */
export interface Lookup { id: string; label: string; hint?: string; }

/**
 * Company user administration — talks to /api/v1/users via ApiService. Mirrors the 15
 * backend endpoints one-to-one; no mock data. The optimistic-lock `version` travels in
 * the PUT body, matching UserController.
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly api = inject(ApiService);

  // ---- reads ----------------------------------------------------------------
  list(query: PageQuery) { return this.api.page<AppUser>(API.users, query); }
  get(id: string) { return this.api.get<UserProfile>(`${API.users}/${id}`); }

  // ---- writes ---------------------------------------------------------------
  create(body: CreateUserRequest) { return this.api.post<UserProfile>(API.users, body); }
  update(id: string, body: UpdateUserRequest) { return this.api.put<UserProfile>(`${API.users}/${id}`, body); }
  remove(id: string) { return this.api.delete<void>(`${API.users}/${id}`); }

  // ---- lifecycle ------------------------------------------------------------
  activate(id: string) { return this.api.patch<UserProfile>(`${API.users}/${id}/activate`, {}); }
  deactivate(id: string) { return this.api.patch<UserProfile>(`${API.users}/${id}/deactivate`, {}); }
  lock(id: string, reason: string) { return this.api.patch<UserProfile>(`${API.users}/${id}/lock`, { reason }); }
  unlock(id: string) { return this.api.patch<UserProfile>(`${API.users}/${id}/unlock`, {}); }

  // ---- passwords ------------------------------------------------------------
  resetPassword(id: string, newPassword: string, mustChangeOnNextLogin: boolean) {
    return this.api.patch<void>(`${API.users}/${id}/reset-password`, { newPassword, mustChangeOnNextLogin });
  }
  changePassword(id: string, currentPassword: string, newPassword: string) {
    return this.api.patch<void>(`${API.users}/${id}/change-password`, { currentPassword, newPassword });
  }

  // ---- roles & placement ----------------------------------------------------
  /** Returns the user's full role-code list after the add. */
  assignRole(id: string, roleId: string) { return this.api.post<string[]>(`${API.users}/${id}/roles`, { roleId }); }
  removeRole(id: string, roleId: string) { return this.api.delete<void>(`${API.users}/${id}/roles/${roleId}`); }
  assignBranch(id: string, branchId: string | null) { return this.api.patch<UserProfile>(`${API.users}/${id}/branch`, { id: branchId }); }
  assignHub(id: string, hubId: string | null) { return this.api.patch<UserProfile>(`${API.users}/${id}/hub`, { id: hubId }); }

  // ---- lookups for dropdowns (live list endpoints, no mock) -----------------
  /** `/roles/assignable` — the picker endpoint, not the paged admin search: a company's
   *  ACTIVE roles only, and the one role-read a BRANCH_MANAGER is granted (they staff
   *  their own branch and need a role to assign). */
  roles() {
    return this.api.get<CompanyRole[]>(`${API.roles}/assignable`);
  }
  branches() {
    return this.api
      .page<Branch>(API.branches, { page: 0, size: 100, sort: 'branchName,asc' })
      .pipe(map((p): Lookup[] => p.content.map((b) => ({ id: b.id, label: b.branchName, hint: b.branchCode }))));
  }
  /** No Hub module exists yet (`GET /hubs` 404s — see app.routes.ts's commented-out hubs
   *  route) — calling it anyway meant every `/users` page load, for every role, toasted a
   *  spurious "Endpoint not found" via the global 404 branch of `error.interceptor.ts`,
   *  even though this lookup's own caller already degrades empty lookups silently.
   *  Empty until the module ships, same honesty pattern as the dashboard's hub summary. */
  hubs() {
    return of<Lookup[]>([]);
  }
  /** Active company users as reporting-manager options; excludes `self` when editing. */
  managers(self?: string) {
    return this.api
      .page<AppUser>(API.users, { page: 0, size: 100, sort: 'firstName,asc', status: 'ACTIVE' })
      .pipe(map((p): Lookup[] => p.content
        .filter((u) => u.id !== self)
        .map((u) => ({ id: u.id, label: u.displayName, hint: u.designation || u.email }))));
  }
}
