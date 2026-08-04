import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import {
  Rate, RateResponse, CreateRateRequest, UpdateRateRequest,
  RateCalculationRequest, RateCalculationResponse
} from '@core/models/rate.model';

/**
 * Rate Master administration — talks to /api/v1/rates via ApiService, mirroring
 * RateController one-to-one; no mock data. The optimistic-lock `version` travels in the
 * PUT body. Status (activate/deactivate) has its own endpoint. `calculate` is read-only —
 * it prices a shipment without booking it, the seam Shipment Booking will eventually call.
 */
@Injectable({ providedIn: 'root' })
export class RateService {
  private readonly api = inject(ApiService);

  // ---- reads ----------------------------------------------------------------
  list(query: PageQuery) { return this.api.page<Rate>(API.rates, query); }
  get(id: string) { return this.api.get<RateResponse>(`${API.rates}/${id}`); }

  /** Every ACTIVE rate for one Route + Service Type + Package Type + Payment Mode
   *  combination — the sibling slabs shown by the Weight Slab Grid. */
  siblings(routeId: string, serviceTypeId: string, packageTypeId: string, paymentModeId: string) {
    return this.api.page<Rate>(API.rates, {
      page: 0, size: 100, sort: 'minimumWeight,asc',
      routeId, serviceTypeId, packageTypeId, paymentModeId
    });
  }

  // ---- writes -----------------------------------------------------------------
  create(body: CreateRateRequest) { return this.api.post<RateResponse>(API.rates, body); }
  update(id: string, body: UpdateRateRequest) {
    return this.api.put<RateResponse>(`${API.rates}/${id}`, body);
  }

  // ---- lifecycle (idempotent) -------------------------------------------------
  activate(id: string) { return this.api.patch<RateResponse>(`${API.rates}/${id}/activate`, {}); }
  deactivate(id: string) { return this.api.patch<RateResponse>(`${API.rates}/${id}/deactivate`, {}); }

  // ---- calculation --------------------------------------------------------------
  calculate(body: RateCalculationRequest) {
    return this.api.post<RateCalculationResponse>(`${API.rates}/calculate`, body);
  }
}
