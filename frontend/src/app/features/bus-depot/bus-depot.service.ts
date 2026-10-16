import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { BusCrossingPick, BusTimetableBoard, BusTimetableRow, BusTimetableSummary, BusCrossingRoute, ShipmentBusCrossing, BusDepot, BusDepotStatus, BusDivision, BusDepotType, SaveBusDepotRequest, UpdateBusDepotRequest } from '@core/models/bus-depot.model';

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

  // ---- crossing routes ---------------------------------------------------------
  crossings(filter: { status?: BusDepotStatus; depotId?: string } = {}) { return this.api.get<BusCrossingRoute[]>(API.busCrossings, filter); }
  crossing(id: string) { return this.api.get<BusCrossingRoute>(`${API.busCrossings}/${id}`); }
  /** Every saved crossing for a trip (by depots, or by the branches whose depots count) — suggestions to pick from. */
  crossingLookup(q: { fromDepotId?: string; toDepotId?: string; fromBranchId?: string; toBranchId?: string }) {
    return this.api.get<BusCrossingRoute[]>(`${API.busCrossings}/lookup`, q);
  }
  updateCrossing(id: string, body: BusCrossingPick) { return this.api.put<BusCrossingRoute>(`${API.busCrossings}/${id}`, body); }
  saveCrossing(body: BusCrossingPick) { return this.api.post<BusCrossingRoute>(API.busCrossings, body); }
  activateCrossing(id: string) { return this.api.patch<BusCrossingRoute>(`${API.busCrossings}/${id}/activate`, {}); }
  deactivateCrossing(id: string) { return this.api.patch<BusCrossingRoute>(`${API.busCrossings}/${id}/deactivate`, {}); }
  removeCrossing(id: string) { return this.api.delete<void>(`${API.busCrossings}/${id}`); }
  shipmentCrossing(shipmentId: string) { return this.api.get<ShipmentBusCrossing | null>(`${API.busCrossings}/shipments/${shipmentId}`); }
  setShipmentCrossing(shipmentId: string, body: BusCrossingPick & { saveAsRoute?: boolean }) {
    return this.api.put<ShipmentBusCrossing>(`${API.busCrossings}/shipments/${shipmentId}`, body);
  }
  clearShipmentCrossing(shipmentId: string) { return this.api.delete<void>(`${API.busCrossings}/shipments/${shipmentId}`); }

  // ---- timetable boards --------------------------------------------------------
  timetables() { return this.api.get<BusTimetableSummary[]>(API.busTimetables); }
  timetable(depotId: string) { return this.api.get<BusTimetableBoard>(`${API.busTimetables}/depots/${depotId}`); }
  saveTimetable(depotId: string, rows: BusTimetableRow[]) {
    return this.api.put<BusTimetableBoard>(`${API.busTimetables}/depots/${depotId}`, { rows });
  }
}
