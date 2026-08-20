import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { EwayBill, CreateEwayBillRequest, UpdateEwayBillRequest } from '@core/models/shipment.model';

/**
 * E-Way Bill Management — talks to /api/v1/eway-bills, mirroring `EwayBillController`
 * one-to-one. Shipment Booking itself carries E-Way Bill data inline in `POST`/
 * `PUT /shipments` (see `ShipmentService`); this service is for managing an already-created
 * E-Way Bill afterward — validating, uploading its document, or cancelling it — from
 * Shipment Details.
 */
@Injectable({ providedIn: 'root' })
export class EwayBillService {
  private readonly api = inject(ApiService);

  get(id: string) { return this.api.get<EwayBill>(`${API.ewayBills}/${id}`); }

  create(body: CreateEwayBillRequest) { return this.api.post<EwayBill>(API.ewayBills, body); }

  update(id: string, body: UpdateEwayBillRequest) {
    return this.api.put<EwayBill>(`${API.ewayBills}/${id}`, body);
  }

  /** Re-checks the row's own current fields and moves it to VALIDATED or INVALID — only a
   *  VALIDATED E-Way Bill lets AWB generation proceed where one is mandatory. */
  validate(id: string) { return this.api.post<EwayBill>(`${API.ewayBills}/${id}/validate`, {}); }

  /** PDF, JPG or PNG only. */
  upload(id: string, file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.api.post<{ url: string }>(`${API.ewayBills}/${id}/upload`, body);
  }

  cancel(id: string, remarks?: string) {
    const query = remarks ? `?remarks=${encodeURIComponent(remarks)}` : '';
    return this.api.post<EwayBill>(`${API.ewayBills}/${id}/cancel${query}`, {});
  }
}
