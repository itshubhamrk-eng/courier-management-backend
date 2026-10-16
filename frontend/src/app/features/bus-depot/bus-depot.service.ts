import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { BusDepot, BusDepotStatus, BusDivision, BusDepotType, SaveBusDepotRequest, UpdateBusDepotRequest } from '@core/models/bus-depot.model';

@Injectable({ providedIn: 'root' })
export class BusDepotService {
  private readonly api = inject(ApiService);

  list(filter: { status?: BusDepotStatus; type?: BusDepotType; branchId?: string; divisionId?: string; search?: string } = {}) {
    return this.api.get<BusDepot[]>(API.busDepots, filter);
  }
  get(id: string) { return this.api.get<BusDepot>(`${API.busDepots}/${id}`); }
  create(body: SaveBusDepotRequest) { return this.api.post<BusDepot>(API.busDepots, body); }
  update(id: string, body: UpdateBusDepotRequest) { return this.api.put<BusDepot>(`${API.busDepots}/${id}`, body); }
  activate(id: string) { return this.api.patch<BusDepot>(`${API.busDepots}/${id}/activate`, {}); }
  deactivate(id: string) { return this.api.patch<BusDepot>(`${API.busDepots}/${id}/deactivate`, {}); }
  remove(id: string) { return this.api.delete<void>(`${API.busDepots}/${id}`); }

  // ---- divisions ---------------------------------------------------------------
  divisions(filter: { status?: BusDepotStatus; search?: string } = {}) { return this.api.get<BusDivision[]>(API.busDivisions, filter); }
  division(id: string) { return this.api.get<BusDivision>(`${API.busDivisions}/${id}`); }
  createDivision(body: { divisionCode: string; divisionName: string }) { return this.api.post<BusDivision>(API.busDivisions, body); }
  updateDivision(id: string, body: { divisionCode: string; divisionName: string; version: number }) {
    return this.api.put<BusDivision>(`${API.busDivisions}/${id}`, body);
  }
  activateDivision(id: string) { return this.api.patch<BusDivision>(`${API.busDivisions}/${id}/activate`, {}); }
  deactivateDivision(id: string) { return this.api.patch<BusDivision>(`${API.busDivisions}/${id}/deactivate`, {}); }
  removeDivision(id: string) { return this.api.delete<void>(`${API.busDivisions}/${id}`); }
}
