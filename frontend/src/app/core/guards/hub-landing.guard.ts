import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { catchError, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { AppRole } from '../models/role.model';
import { MasterDataService } from '@features/masters/master-data.service';

/** A user staffed at a HUB branch lands on the Hub Dashboard instead of the generic
 *  branch dashboard — the hub's own work (incoming, at hub, sorting, load sheets) lives there.
 *  Only for the roles that may open it (same set as the hub-operations routes): hub counter staff
 *  (booking/delivery operators, accounts) would otherwise be bounced to "Access denied" on login. */
const HUB_DASHBOARD_ROLES = [AppRole.COMPANY_ADMIN, AppRole.HUB_MANAGER];
export const hubLandingGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const branchId = auth.user()?.branchId;
  if (!branchId || !auth.hasAnyRole(HUB_DASHBOARD_ROLES)) return true;
  return inject(MasterDataService).branchDirectory().pipe(
    map((list) => list.find((b) => b.id === branchId)?.branchType === 'HUB'
      ? router.createUrlTree(['/hub-operations/dashboard']) : true),
    catchError(() => of(true))
  );
};
