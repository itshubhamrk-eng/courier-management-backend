import { Injectable, effect, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { MasterDataService } from '@features/masters/master-data.service';
import { AuthService } from './auth.service';

/**
 * Is the signed-in user staffed at a Pickup-Delivery Partner? A partner only collects and delivers,
 * so the sidebar narrows to that work (see `NavigationService`). Read from the branch directory by
 * the user's own `branchId`; false for anyone without a branch (admins) or when it cannot be read —
 * failing open to the full menu, never closed.
 */
@Injectable({ providedIn: 'root' })
export class PartnerContextService {
  private readonly auth = inject(AuthService);
  private readonly masters = inject(MasterDataService);

  /** A signal (not a plain field): resolved asynchronously under OnPush. */
  readonly isPartner = signal(false);

  constructor() {
    effect(() => {
      const branchId = this.auth.user()?.branchId;
      if (!branchId) { this.isPartner.set(false); return; }
      this.masters.branchDirectory().pipe(catchError(() => of([]))).subscribe((list) =>
        this.isPartner.set(list.find((b) => b.id === branchId)?.branchType === 'PICKUP_DELIVERY_PARTNER'));
    });
  }
}
