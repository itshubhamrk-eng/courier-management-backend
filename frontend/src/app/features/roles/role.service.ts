import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  CompanyRole, RoleProfile, CreateRoleRequest, UpdateRoleRequest
} from '@core/models/role.model';
import { PageQuery } from '@core/models/page.model';

/**
 * Company role administration — talks to /api/v1/roles via ApiService. Mirrors the backend
 * endpoints one-to-one; no mock data. The optimistic-lock `version` travels in the PUT
 * body, matching RoleController. "Clone" has no backend endpoint: it is a create prefilled
 * from an existing role's profile (see role-create).
 */
@Injectable({ providedIn: 'root' })
export class RoleService {
  private readonly api = inject(ApiService);

  // ---- reads ----------------------------------------------------------------
  list(query: PageQuery) { return this.api.page<CompanyRole>(API.roles, query); }
  get(id: string) { return this.api.get<RoleProfile>(`${API.roles}/${id}`); }
  assignable() { return this.api.get<CompanyRole[]>(`${API.roles}/assignable`); }

  // ---- writes ---------------------------------------------------------------
  create(body: CreateRoleRequest) { return this.api.post<RoleProfile>(API.roles, body); }
  update(id: string, body: UpdateRoleRequest) { return this.api.put<RoleProfile>(`${API.roles}/${id}`, body); }
  remove(id: string) { return this.api.delete<void>(`${API.roles}/${id}`); }

  // ---- lifecycle (idempotent) ----------------------------------------------
  activate(id: string) { return this.api.patch<RoleProfile>(`${API.roles}/${id}/activate`, {}); }
  deactivate(id: string) { return this.api.patch<RoleProfile>(`${API.roles}/${id}/deactivate`, {}); }
}
