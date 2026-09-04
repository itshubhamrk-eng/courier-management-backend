/** POD Auto Verification — see MEMORY/modules/pod-verification.md. Mirrors backend
 *  PodVerificationResponse. AI never itself moves a shipment to DELIVERED; a PASS/approved
 *  result only unlocks the existing "Complete Delivery" action. */
export type PodVerificationStatus = 'PASS' | 'REVIEW' | 'FAIL';

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
  detectedDate: string | null;
  signatureDetected: boolean;
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
  deliveryBranchId: string;
  receiverName: string | null;
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
