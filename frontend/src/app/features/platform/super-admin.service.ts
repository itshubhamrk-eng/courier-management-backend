import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { CreateSuperAdminRequest, SuperAdminUser } from '@core/models/company.model';

/**
 * Platform-tier accounts. `SUPER_ADMIN` only, reads included — the list of who holds the
 * highest privilege on the platform is exactly the list a lesser account would most like
 * to have.
 *
 * <p>Separate from `UserService`, which manages a company's own staff and is scoped to
 * one company. These accounts belong to no company.
 */
@Injectable({ providedIn: 'root' })
export class SuperAdminService {
  private readonly api = inject(ApiService);

  /** Every account that can act outside a single company, SUPER_ADMIN and PLATFORM_ADMIN. */
  list() {
    return this.api.get<SuperAdminUser[]>(`${API.superAdmin}/users`);
  }

  /**
   * Create another platform operator.
   *
   * Omit `password` and the server generates one and returns it in
   * `temporaryPassword` — once. It is never retrievable again, so the caller must show
   * it immediately and say so.
   */
  create(body: CreateSuperAdminRequest) {
    return this.api.post<SuperAdminUser>(`${API.superAdmin}/users`, body);
  }
}
