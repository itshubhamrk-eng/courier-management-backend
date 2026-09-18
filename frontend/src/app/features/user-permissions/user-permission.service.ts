import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  MenuItemNode, MenuPermissionNode, UserMenuPermissionUpdateRequest, UserPermissionUpdateResult
} from '@core/models/menu-permission.model';

/**
 * Menu + Permission Management's user-facing half: the menu hierarchy and one user's
 * permission matrix (role defaults + overrides). Mirrors the backend endpoints
 * one-to-one — see MEMORY/modules/permission.md.
 */
@Injectable({ providedIn: 'root' })
export class UserPermissionService {
  private readonly api = inject(ApiService);

  /** The plain menu hierarchy, nested — mostly useful for admin tooling; the user
   *  matrix screen normally calls {@link menuPermissions} instead, which already
   *  carries the tree plus every leaf's role-default/effective/overridden state. */
  menuTree() { return this.api.get<MenuItemNode[]>(`${API.menuItems}/tree`); }

  /** One user's full menu tree, annotated with role default / effective / overridden
   *  per CRUD action. */
  menuPermissions(userId: string) {
    return this.api.get<MenuPermissionNode>(API.userMenuPermissions(userId));
  }

  /** Saves the whole desired matrix; the backend persists only what differs from the
   *  role default. */
  save(userId: string, request: UserMenuPermissionUpdateRequest) {
    return this.api.put<UserPermissionUpdateResult>(API.userMenuPermissions(userId), request);
  }
}
