import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import {
  Shipment, ShipmentResponse, ShipmentCharge, ShipmentStatusHistoryEntry, ShipmentDocument,
  ShipmentItem, CreateShipmentRequest, UpdateShipmentRequest, AddShipmentDocumentRequest,
  PricingRequest, PricingResponse, TimelineStep, ShipmentSearchRequest, ShipmentSummaryStats,
  BranchCommissionSummary
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
  /** Unpaged aggregates for the Booking/Delivery Report summary row — same filters as
   *  `list`, minus paging. */
  summary(filters: ShipmentSearchRequest) {
    return this.api.get<ShipmentSummaryStats>(`${API.shipments}/summary`, filters as Record<string, unknown>);
  }
  /** Branch-wise commission totals for the Commission Report's summary table — same filters as `list`. */
  commissionSummary(filters: ShipmentSearchRequest) {
    return this.api.get<BranchCommissionSummary[]>(
      `${API.shipments}/commission-summary`, filters as Record<string, unknown>);
  }
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

  /** Uploads a shipment photo (JPEG/PNG/WEBP/HEIC) and records it against the shipment
   *  immediately — unlike POD upload there is no separate confirm step, so the returned
   *  URL is only for showing a fresh preview, not for feeding into another call. */
  uploadImage(id: string, file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.api.post<{ url: string }>(`${API.shipments}/${id}/image-upload`, body);
  }

  // ---- pricing preview (Pricing Engine, not this module's own) -----------------
  preview(body: PricingRequest) {
    return this.api.post<PricingResponse>(`${API.pricing}/calculate`, body);
  }
}
