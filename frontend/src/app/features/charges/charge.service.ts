import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import {
  Charge, ChargeResponse, CreateChargeRequest, UpdateChargeRequest,
  ChargeSetting, CreateChargeSettingRequest, UpdateChargeSettingRequest
} from '@core/models/charge.model';

/**
 * Charge administration — talks to /api/v1/charges and /api/v1/charges/{chargeId}/settings
 * via ApiService, mirroring ChargeController/ChargeSettingController one-to-one; no mock
 * data. The optimistic-lock `version` travels in the PUT body. Status (activate/
 * deactivate) has its own endpoints, for both a charge and a setting.
 */
@Injectable({ providedIn: 'root' })
export class ChargeService {
  private readonly api = inject(ApiService);

  // ---- charge reads -----------------------------------------------------------
  list(query: PageQuery) { return this.api.page<Charge>(API.charges, query); }
  get(id: string) { return this.api.get<ChargeResponse>(`${API.charges}/${id}`); }

  // ---- charge writes ------------------------------------------------------------
  create(body: CreateChargeRequest) { return this.api.post<ChargeResponse>(API.charges, body); }
  update(id: string, body: UpdateChargeRequest) {
    return this.api.put<ChargeResponse>(`${API.charges}/${id}`, body);
  }
  delete(id: string) { return this.api.delete<void>(`${API.charges}/${id}`); }

  // ---- charge lifecycle (idempotent) ---------------------------------------------
  activate(id: string) { return this.api.patch<ChargeResponse>(`${API.charges}/${id}/activate`, {}); }
  deactivate(id: string) { return this.api.patch<ChargeResponse>(`${API.charges}/${id}/deactivate`, {}); }

  // ---- charge setting reads -------------------------------------------------------
  listSettings(chargeId: string) {
    return this.api.get<ChargeSetting[]>(API.chargeSettings(chargeId));
  }
  getSetting(chargeId: string, id: string) {
    return this.api.get<ChargeSetting>(`${API.chargeSettings(chargeId)}/${id}`);
  }

  // ---- charge setting writes --------------------------------------------------------
  createSetting(chargeId: string, body: CreateChargeSettingRequest) {
    return this.api.post<ChargeSetting>(API.chargeSettings(chargeId), body);
  }
  updateSetting(chargeId: string, id: string, body: UpdateChargeSettingRequest) {
    return this.api.put<ChargeSetting>(`${API.chargeSettings(chargeId)}/${id}`, body);
  }
  deleteSetting(chargeId: string, id: string) {
    return this.api.delete<void>(`${API.chargeSettings(chargeId)}/${id}`);
  }

  // ---- charge setting lifecycle (idempotent) -----------------------------------------
  activateSetting(chargeId: string, id: string) {
    return this.api.patch<ChargeSetting>(`${API.chargeSettings(chargeId)}/${id}/activate`, {});
  }
  deactivateSetting(chargeId: string, id: string) {
    return this.api.patch<ChargeSetting>(`${API.chargeSettings(chargeId)}/${id}/deactivate`, {});
  }
}
