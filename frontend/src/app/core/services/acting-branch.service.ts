import { Injectable, computed, inject, signal } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';

const STORAGE_KEY = 'cs.actingBranch';

/**
 * Lets a user with no branch of their own (a COMPANY_ADMIN) work a branch's or hub's
 * In Scan / DRS / Delivery screens by picking which one to act as. A user who has a
 * branch is never affected — `effectiveId` is always their own. Client-side only: the
 * backend already accepts any branch id from a COMPANY_ADMIN, so nothing is granted here
 * that the API did not already allow. Kept per browser tab (sessionStorage).
 */
@Injectable({ providedIn: 'root' })
export class ActingBranchService {
  private readonly auth = inject(AuthService);

  private readonly actingId = signal<string | null>(this.read());

  /** True when the signed-in user has no branch and so may choose one to act as. */
  readonly canAct = computed(() => !this.auth.user()?.branchId);
  readonly effectiveId = computed(() => this.auth.user()?.branchId ?? (this.canAct() ? this.actingId() : null));
  readonly acting = this.actingId.asReadonly();

  set(id: string | null): void {
    this.actingId.set(id);
    try {
      if (id) sessionStorage.setItem(STORAGE_KEY, id); else sessionStorage.removeItem(STORAGE_KEY);
    } catch { /* storage unavailable — selection just doesn't survive a reload */ }
  }

  private read(): string | null {
    try { return sessionStorage.getItem(STORAGE_KEY); } catch { return null; }
  }
}
