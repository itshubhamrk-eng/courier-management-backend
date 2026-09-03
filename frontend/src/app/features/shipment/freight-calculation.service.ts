import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { FreightCalculationRequest, FreightCalculationResponse } from '@core/models/district-level-freight.model';

/**
 * Shipment Booking's live freight preview — POST /district-level-freight/calculate. Pure
 * preview: the backend recomputes and re-verifies this same figure authoritatively at
 * Confirm Booking (`ShipmentServiceImpl.create`/`update`), never trusting what this call
 * returned.
 */
@Injectable({ providedIn: 'root' })
export class FreightCalculationService {
  private readonly api = inject(ApiService);

  calculate(body: FreightCalculationRequest) {
    return this.api.post<FreightCalculationResponse>(API.districtLevelFreightCalculate, body);
  }
}
