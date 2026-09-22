/** POD Auto Verification — see MEMORY/modules/pod-verification.md. Mirrors backend
 *  PodVerificationResponse. AI never itself moves a shipment to DELIVERED; a PASS/approved
 *  result only unlocks the existing "Complete Delivery" action. */
export type PodVerificationStatus = 'PASS' | 'PENDING' | 'FAIL';

/** Paper-register-style "Status" column on a company-level POD upload — independent of
 *  PodVerificationStatus (the AI/review outcome) and of the shipment's own DELIVERED status
 *  machine. Purely descriptive. */
export type PodEntryStatus = 'DELIVERED' | 'NOT_DELIVERED' | 'RETURNED' | 'RTO';

export interface PodVerification {
  id: string;
  shipmentId: string;
  shipmentNumber: string | null;
  trackingNumber: string | null;
  podDocumentId: string | null;
  photoUrl: string | null;
  signatureUrl: string | null;
  verificationStatus: PodVerificationStatus;
  verificationScore: number;
  verificationReasons: string[];
  detectedReceiverName: string | null;
  detectedAwb: string | null;
  /** Shipment/AWB/LR number actually read off the photo's own content by the AI provider —
   *  distinct from detectedAwb, which may just echo a claimed/known value. */
  detectedShipmentNumber: string | null;
  detectedDate: string | null;
  /** Paper-register fields — set only by a company-level upload, null for the AI/verify path. */
  deliveryDate: string | null;
  deliveredBy: string | null;
  podDate: string | null;
  podTime: string | null;
  entryStatus: PodEntryStatus | null;
  remark: string | null;
  signatureDetected: boolean;
  /** Company/receiver stamp or seal visible on the capture — distinct from a signature. */
  stampDetected: boolean;
  imageQuality: string | null;
  aiProvider: string;
  aiModel: string;
  verifiedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewRemarks: string | null;
}

/** Body of POST /shipments/{id}/pod/review. */
export interface PodReviewRequest {
  approve: boolean;
  remarks?: string | null;
}

/** One row of the POD Review table — GET /pod/delivered. Every DELIVERED shipment, whether
 *  or not a POD verification ever ran against it; every POD-related field is null when it
 *  hasn't. */
export interface DeliveredShipmentPod {
  shipmentId: string;
  shipmentNumber: string;
  trackingNumber: string;
  bookingBranchId: string;
  deliveryBranchId: string;
  receiverName: string | null;
  receivedAt: string | null;
  deliveredAt: string | null;
  podVerificationId: string | null;
  verificationStatus: PodVerificationStatus | null;
  verificationScore: number | null;
  verificationReasons: string[] | null;
  photoUrl: string | null;
  signatureUrl: string | null;
  aiProvider: string | null;
  aiModel: string | null;
}

/** One row of POST /pod/bulk-upload — one per uploaded file, submission order. `verification`
 *  is only present when matchStatus is 'MATCHED'. */
export type BulkPodUploadMatchStatus = 'MATCHED' | 'NO_MATCH' | 'AMBIGUOUS' | 'INVALID_STATUS' | 'ERROR';

export interface BulkPodUploadRow {
  filename: string | null;
  matchStatus: BulkPodUploadMatchStatus;
  message: string;
  detectedShipmentNumber: string | null;
  detectedAwb: string | null;
  shipmentId: string | null;
  shipmentNumber: string | null;
  trackingNumber: string | null;
  verification: PodVerification | null;
}
