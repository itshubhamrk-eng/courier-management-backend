import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { catchError, map } from 'rxjs/operators';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { MasterDataService } from '@features/masters/master-data.service';

/** A user staffed at a HUB branch lands on the Hub Dashboard instead of the generic
 *  branch dashboard — the hub's own work (incoming, at hub, sorting, load sheets) lives there. */
export const hubLandingGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const branchId = auth.user()?.branchId;
  if (!branchId) return true;
  return inject(MasterDataService).branchDirectory().pipe(
    map((list) => list.find((b) => b.id === branchId)?.branchType === 'HUB'
      ? router.createUrlTree(['/hub-operations/dashboard']) : true),
    catchError(() => of(true))
  );
};
