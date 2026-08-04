import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import {
  Shipment, ShipmentResponse, ShipmentCharge, ShipmentStatusHistoryEntry, ShipmentDocument,
  ShipmentItem, CreateShipmentRequest, UpdateShipmentRequest, AddShipmentDocumentRequest,
  PricingRequest, PricingResponse, TimelineStep
} from '@core/models/shipment.model';

/**
 * Shipment Booking — talks to /api/v1/shipments via ApiService, mirroring
 * ShipmentController one-to-one; no mock data. The optimistic-lock `version` travels in
 * the PUT body. `preview` calls the Pricing Engine directly (`POST /pricing/calculate`)
 * for the wizard's live Step 3 preview — read-only, prices nothing, books nothing; the
 * actual booking re-prices through the same engine inside `POST /shipments`.
 */
@Injectable({ providedIn: 'root' })
export class ShipmentService {
  private readonly api = inject(ApiService);

  // ---- reads ------------------------------------------------------------------
  list(query: PageQuery) { return this.api.page<Shipment>(API.shipments, query); }
  get(id: string) { return this.api.get<ShipmentResponse>(`${API.shipments}/${id}`); }
  getByTrackingNumber(trackingNumber: string) {
    return this.api.get<ShipmentResponse>(`${API.shipments}/track/${trackingNumber}`);
  }
  items(id: string) { return this.api.get<ShipmentItem[]>(`${API.shipments}/${id}/items`); }
  charges(id: string) { return this.api.get<ShipmentCharge>(`${API.shipments}/${id}/charges`); }
  history(id: string) { return this.api.get<ShipmentStatusHistoryEntry[]>(`${API.shipments}/${id}/history`); }
  documents(id: string) { return this.api.get<ShipmentDocument[]>(`${API.shipments}/${id}/documents`); }
  timeline(id: string) { return this.api.get<TimelineStep[]>(`${API.shipments}/${id}/timeline`); }

  // ---- writes -----------------------------------------------------------------
  create(body: CreateShipmentRequest) { return this.api.post<ShipmentResponse>(API.shipments, body); }
  update(id: string, body: UpdateShipmentRequest) {
    return this.api.put<ShipmentResponse>(`${API.shipments}/${id}`, body);
  }
  cancel(id: string, remarks?: string) {
    const query = remarks ? `?remarks=${encodeURIComponent(remarks)}` : '';
    return this.api.post<ShipmentResponse>(`${API.shipments}/${id}/cancel${query}`, {});
  }
  addDocument(id: string, body: AddShipmentDocumentRequest) {
    return this.api.post<ShipmentDocument>(`${API.shipments}/${id}/documents`, body);
  }

  // ---- pricing preview (Pricing Engine, not this module's own) -----------------
  preview(body: PricingRequest) {
    return this.api.post<PricingResponse>(`${API.pricing}/calculate`, body);
  }
}
