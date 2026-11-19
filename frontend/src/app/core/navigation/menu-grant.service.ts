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

  /** Menus the user's roles hold (minus denied). Only trusted once {@link loaded} is true. */
  private readonly _allowed = signal<ReadonlySet<string>>(new Set());
  readonly allowed = this._allowed.asReadonly();
  /** False until a fetch succeeds, so a failed fetch falls back to the permission rule. */
  private readonly _loaded = signal(false);
  readonly loaded = this._loaded.asReadonly();
  /** True once the fetch for the current session has answered (success or failure), so a
   *  route guard can wait for it without hanging on an error. */
  private readonly _settled = signal(false);
  readonly settled = this._settled.asReadonly();

  constructor() {
    effect(() => {
      this.auth.user();
      if (!this.auth.isAuthenticated()) { this.reset(); return; }
      this._settled.set(false);
      this.api.get<{ deniedMenus: string[]; allowedMenus?: string[] }>(API.myMenuGrants).subscribe({
        next: (r) => {
          this._denied.set(new Set(r.deniedMenus ?? []));
          this._allowed.set(new Set(r.allowedMenus ?? []));
          this._loaded.set(r.allowedMenus !== undefined);
          this._settled.set(true);
        },
        error: () => { this.reset(); this._settled.set(true); }
      });
    });
  }

  private reset(): void {
    this._denied.set(new Set());
    this._allowed.set(new Set());
    this._loaded.set(false);
    this._settled.set(false);
  }
}
