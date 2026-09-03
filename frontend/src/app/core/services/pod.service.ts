import { HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ApiService, SILENT_ERRORS } from './api.service';
import { API } from '@core/config/api-endpoints';
import { PodVerification, PodReviewRequest } from '@core/models/pod.model';

/** POD Auto Verification — talks to /api/v1/shipments/{id}/pod/**, mirroring
 *  PodVerificationController one-to-one. See MEMORY/modules/pod-verification.md. */
@Injectable({ providedIn: 'root' })
export class PodService {
  private readonly api = inject(ApiService);

  /** Uploads the delivery photo (required) and signature (optional) and runs AI
   *  verification in one call. Shipment must be OUT_FOR_DELIVERY. */
  verify(shipmentId: string, params: {
    photo: File; signature?: File | null; receiverName: string;
    awbNumber?: string | null; shipmentNumber?: string | null; deliveryDateTime?: string | null;
    /** LR/tracking number decoded live off the label's QR by the capture screen's own camera
     *  scan, before this call — an independent cross-check, not an echo of `awbNumber`/
     *  `shipmentNumber` (see `HeuristicPodVerificationProvider`'s `qrScanValue` check).
     *  Omitted when the operator didn't scan; the backend then falls back to decoding the QR
     *  out of the uploaded photo itself. */
    qrScanValue?: string | null;
  }) {
    const body = new FormData();
    body.append('photo', params.photo);
    if (params.signature) body.append('signature', params.signature);
    body.append('receiverName', params.receiverName);
    if (params.awbNumber) body.append('awbNumber', params.awbNumber);
    if (params.shipmentNumber) body.append('shipmentNumber', params.shipmentNumber);
    if (params.deliveryDateTime) body.append('deliveryDateTime', params.deliveryDateTime);
    if (params.qrScanValue) body.append('qrScanValue', params.qrScanValue);
    return this.api.post<PodVerification>(`${API.pod(shipmentId)}/verify`, body);
  }

  private readonly silent = new HttpContext().set(SILENT_ERRORS, true);

  /** Latest verification for a shipment — silent 404 when none has been run yet, so a
   *  fresh Delivery page load doesn't toast an error for the common case. */
  getLatest(shipmentId: string) {
    return this.api.get<PodVerification>(`${API.pod(shipmentId)}/verification`, undefined, this.silent);
  }

  review(shipmentId: string, request: PodReviewRequest) {
    return this.api.post<PodVerification>(`${API.pod(shipmentId)}/review`, request);
  }

  /** The Manual Review screen's worklist — every REVIEW-status verification, oldest first. */
  pendingReview() {
    return this.api.get<PodVerification[]>('/pod/pending-review');
  }
}
