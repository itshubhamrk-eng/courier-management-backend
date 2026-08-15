import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { AddressDistanceResponse, AddressType } from '@core/models/address-distance.model';

/**
 * Talks to /api/v1/distances. Branch-only from the frontend for now — the backend also
 * exposes a /customer-addresses resolve endpoint, but customer addresses aren't geocoded
 * on save yet, so there is nothing for that path to resolve today.
 */
@Injectable({ providedIn: 'root' })
export class AddressDistanceService {
  private readonly api = inject(ApiService);

  /** Cache-or-resolve — first call for a pair hits the routing provider, every call after
   *  returns the stored row. */
  resolveBranchDistance(fromBranchId: string, toBranchId: string) {
    return this.api.get<AddressDistanceResponse>(`${API.distances}/branches`, { fromBranchId, toBranchId });
  }

  search(query: { addressType?: AddressType; fromId?: string; toId?: string } = {}) {
    return this.api.get<AddressDistanceResponse[]>(API.distances, query);
  }

  refresh(id: string) {
    return this.api.post<AddressDistanceResponse>(`${API.distances}/${id}/refresh`, {});
  }

  remove(id: string) {
    return this.api.delete<void>(`${API.distances}/${id}`);
  }
}
