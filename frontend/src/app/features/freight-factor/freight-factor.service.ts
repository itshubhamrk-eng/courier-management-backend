import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import {
  FreightFactor, CreateFreightFactorRequest, UpdateFreightFactorRequest,
  FreightCalculationRequest, FreightCalculationResponse
} from '@core/models/freight-factor.model';

/**
 * Freight Factor administration — talks to /api/v1/freight-factors via ApiService,
 * mirroring FreightFactorController one-to-one; no mock data. `size: 100` on `list` is a
 * deliberate simplification, not pagination support — this is an admin-maintained grid,
 * expected to stay small, so no pager UI is built for it (same call `RateService.siblings`
 * makes). The optimistic-lock `version` travels in the PUT body. Status (activate/
 * deactivate) has its own endpoint.
 */
@Injectable({ providedIn: 'root' })
export class FreightFactorService {
  private readonly api = inject(ApiService);

  list() {
    return this.api.page<FreightFactor>(API.freightFactors, { page: 0, size: 100, sort: 'fromKm,asc' });
  }

  create(body: CreateFreightFactorRequest) {
    return this.api.post<FreightFactor>(API.freightFactors, body);
  }

  update(id: string, body: UpdateFreightFactorRequest) {
    return this.api.put<FreightFactor>(`${API.freightFactors}/${id}`, body);
  }

  activate(id: string) {
    return this.api.patch<FreightFactor>(`${API.freightFactors}/${id}/activate`, {});
  }

  deactivate(id: string) {
    return this.api.patch<FreightFactor>(`${API.freightFactors}/${id}/deactivate`, {});
  }

  calculate(body: FreightCalculationRequest) {
    return this.api.post<FreightCalculationResponse>(`${API.freightFactors}/calculate`, body);
  }
}
