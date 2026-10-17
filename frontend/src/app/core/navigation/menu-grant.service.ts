import { Injectable, effect, inject, signal } from '@angular/core';
import { ApiService } from '../services/api.service';
import { API } from '../config/api-endpoints';
import { AuthService } from '../auth/auth.service';

/**
 * Sidebar menus an admin explicitly unticked for the signed-in user on the User Permissions
 * screen (node ids from navigation.config.ts). Refetched whenever the session changes. A
 * failed fetch hides nothing: this only ever narrows the sidebar, and the backend stays the
 * enforcement point for data.
 */
@Injectable({ providedIn: 'root' })
export class MenuGrantService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  private readonly _denied = signal<ReadonlySet<string>>(new Set());
  readonly denied = this._denied.asReadonly();

  constructor() {
    effect(() => {
      this.auth.user();
      if (!this.auth.isAuthenticated()) { this._denied.set(new Set()); return; }
      this.api.get<{ deniedMenus: string[] }>(API.myMenuGrants).subscribe({
        next: (r) => this._denied.set(new Set(r.deniedMenus ?? [])),
        error: () => this._denied.set(new Set())
      });
    });
  }
}
