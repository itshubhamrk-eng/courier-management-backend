import { Injectable, inject } from '@angular/core';
import { forkJoin, of, switchMap, type Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { Department, CreateDepartmentRequest, UpdateDepartmentRequest } from '@core/models/department.model';

/**
 * Company department administration — talks to /api/v1/departments via ApiService.
 * Mirrors the backend endpoints one-to-one; no mock data. Unpaged throughout, like
 * RoleService.assignable — a company has a handful of departments, not a catalogue.
 */
@Injectable({ providedIn: 'root' })
export class DepartmentService {
  private readonly api = inject(ApiService);

  // ---- reads ----------------------------------------------------------------
  list() { return this.api.get<Department[]>(API.departments); }
  get(id: string) { return this.api.get<Department>(`${API.departments}/${id}`); }
  /** ACTIVE departments and the roles each offers, for the department picker on user creation. */
  assignable() { return this.api.get<Department[]>(`${API.departments}/assignable`); }

  // ---- writes -----------------------------------------------------------------
  create(body: CreateDepartmentRequest) { return this.api.post<Department>(API.departments, body); }
  update(id: string, body: UpdateDepartmentRequest) { return this.api.put<Department>(`${API.departments}/${id}`, body); }
  remove(id: string) { return this.api.delete<void>(`${API.departments}/${id}`); }

  // ---- lifecycle (idempotent) --------------------------------------------------
  activate(id: string) { return this.api.patch<Department>(`${API.departments}/${id}/activate`, {}); }
  deactivate(id: string) { return this.api.patch<Department>(`${API.departments}/${id}/deactivate`, {}); }

  /**
   * Grants/withdraws `roleId` across departments so the caller's own picker (e.g. the
   * Role form's "Departments" field) ends up matching `desiredDepartmentIds` — there is no
   * single "offer this role here" endpoint, only each department's own full-replacement
   * PUT, so this reads the current ACTIVE departments fresh (for an up-to-date `version`,
   * not whatever the page loaded earlier) and only touches the ones whose membership
   * actually changed.
   */
  syncRoleGrants(roleId: string, desiredDepartmentIds: readonly string[]): Observable<Department[]> {
    const desired = new Set(desiredDepartmentIds);
    return this.list().pipe(switchMap((departments) => {
      const calls = departments
        .filter((d) => d.status === 'ACTIVE')
        .filter((d) => d.roles.some((r) => r.id === roleId) !== desired.has(d.id))
        .map((d) => {
          const roleIds = d.roles.map((r) => r.id);
          const nextRoleIds = desired.has(d.id) ? [...roleIds, roleId] : roleIds.filter((id) => id !== roleId);
          return this.update(d.id, {
            departmentName: d.departmentName, description: d.description ?? null,
            roleIds: nextRoleIds, version: d.version
          });
        });
      return calls.length ? forkJoin(calls) : of([]);
    }));
  }
}
