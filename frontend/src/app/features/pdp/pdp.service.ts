import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { Branch } from '@core/models/branch.model';
import { PageQuery } from '@core/models/page.model';
import {
  CalculatePdpRateRequest, CalculatePdpRateResponse, PdpRate, PdpRateType, SavePdpRateRequest
} from '@core/models/pdp.model';

/**
 * Pickup-Delivery Partners — a Partner is a `Branch` row with `branchType:
 * 'PICKUP_DELIVERY_PARTNER'`; listing reuses `GET /branches?branchType=…`, create/edit reuse the
 * Branch screens. Rate cards have their own endpoint (`/pdp-rates`).
 */
@Injectable({ providedIn: 'root' })
export class PdpService {
  private readonly api = inject(ApiService);

  partners(query: PageQuery) {
    return this.api.page<Branch>(API.branches, { ...query, branchType: 'PICKUP_DELIVERY_PARTNER' });
  }

  rates(filter: { branchId?: string; rateType?: PdpRateType } = {}) { return this.api.get<PdpRate[]>(API.pdpRates, filter); }
  createRate(body: SavePdpRateRequest) { return this.api.post<PdpRate>(API.pdpRates, body); }
  updateRate(id: string, body: SavePdpRateRequest) { return this.api.put<PdpRate>(`${API.pdpRates}/${id}`, body); }
  activateRate(id: string) { return this.api.patch<PdpRate>(`${API.pdpRates}/${id}/activate`, {}); }
  deactivateRate(id: string) { return this.api.patch<PdpRate>(`${API.pdpRates}/${id}/deactivate`, {}); }
  deleteRate(id: string) { return this.api.delete<void>(`${API.pdpRates}/${id}`); }
  calculate(body: CalculatePdpRateRequest) { return this.api.post<CalculatePdpRateResponse>(`${API.pdpRates}/calculate`, body); }
}
