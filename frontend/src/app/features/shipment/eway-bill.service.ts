import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { EwayBill, CreateEwayBillRequest, UpdateEwayBillRequest } from '@core/models/shipment.model';

/**
 * E-Way Bill Management — talks to /api/v1/eway-bills, mirroring `EwayBillController`
 * one-to-one. Auto-generation itself (Part-A at booking, Part-B at Manifest dispatch)
 * happens entirely on the backend; this service is for inspecting an already-created
 * E-Way Bill afterward, retrying a failed generation, uploading a document, or
 * cancelling it — from Shipment Details.
 */
@Injectable({ providedIn: 'root' })
export class EwayBillService {
  private readonly api = inject(ApiService);

  get(id: string) { return this.api.get<EwayBill>(`${API.ewayBills}/${id}`); }

  create(body: CreateEwayBillRequest) { return this.api.post<EwayBill>(API.ewayBills, body); }

  update(id: string, body: UpdateEwayBillRequest) {
    return this.api.put<EwayBill>(`${API.ewayBills}/${id}`, body);
  }

  /** Re-attempts whichever stage last failed — Part-A when no number was ever issued,
   *  Part-B otherwise (or a fresh Part-A when EXPIRED). Only legal from FAILED/EXPIRED. */
  retry(id: string) { return this.api.post<EwayBill>(`${API.ewayBills}/${id}/retry`, {}); }

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
