import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  BulkMovementResult, DispatchManifestRequest, DispatchManifestResponse,
  InScanRequest, OutForDeliveryRequest, DeliverRequest, ShipmentResponse
} from '@core/models/shipment.model';
import { UserService, Lookup } from '@features/users/user.service';

/**
 * Dispatch -> In Scan -> Out For Delivery -> Deliver — talks to
 * /api/v1/shipment-movement, mirroring ShipmentMovementController one-to-one. No mock data.
 * Out Scan is no longer a separate step (V20, on direct request) — adding a shipment to
 * a manifest already is "out scan created"; see ManifestService.create.
 */
@Injectable({ providedIn: 'root' })
export class ShipmentMovementService {
  private readonly api = inject(ApiService);
  private readonly users = inject(UserService);

  dispatch(body: DispatchManifestRequest) {
    return this.api.post<DispatchManifestResponse>(`${API.shipmentMovement}/dispatch`, body);
  }
  inScan(body: InScanRequest) {
    return this.api.post<BulkMovementResult>(`${API.shipmentMovement}/in-scan`, body);
  }
  outForDelivery(body: OutForDeliveryRequest) {
    return this.api.post<BulkMovementResult>(`${API.shipmentMovement}/out-for-delivery`, body);
  }
  deliver(body: DeliverRequest) {
    return this.api.post<ShipmentResponse>(`${API.shipmentMovement}/deliver`, body);
  }

  /** Active company users, for the driver/delivery-user pickers — same shape
   *  BranchService.managers() already uses for its own manager picker. */
  userOptions() {
    return this.users
      .list({ page: 0, size: 100, sort: 'firstName,asc', status: 'ACTIVE' })
      .pipe(map((p): Lookup[] => p.content.map((u) => ({
        id: u.id, label: u.displayName, hint: u.designation || u.email
      }))));
  }
}
