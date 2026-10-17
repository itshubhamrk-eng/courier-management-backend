import { Injectable, effect, inject, signal } from '@angular/core';
import { ApiService } from '../services/api.service';
import { API } from '../config/api-endpoints';
import { AuthService } from '../auth/auth.service';

/**
 * Sidebar menus a SUPER_ADMIN has hidden from the signed-in user's company (node ids from
 * navigation.config.ts). Fetched whenever the session changes — login, logout, company
 * impersonation. A failed fetch leaves everything visible: this only ever hides, and the
 * backend remains the real enforcement point for data.
 */
@Injectable({ providedIn: 'root' })
export class MenuLockService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  private readonly _locked = signal<ReadonlySet<string>>(new Set());
  readonly locked = this._locked.asReadonly();

  constructor() {
    effect(() => {
      // Track the session so a new login / impersonation refetches for the right company.
      this.auth.user();
      if (!this.auth.isAuthenticated()) { this._locked.set(new Set()); return; }
      this.api.get<{ lockedMenus: string[] }>(API.myMenuLocks).subscribe({
        next: (r) => this._locked.set(new Set(r.lockedMenus ?? [])),
        error: () => this._locked.set(new Set())
      });
    });
  }
}
