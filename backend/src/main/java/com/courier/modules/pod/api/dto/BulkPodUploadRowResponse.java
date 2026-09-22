package com.courier.modules.pod.api.dto;

import java.util.UUID;

/** One row of {@code POST /api/v1/pod/bulk-upload} — one per uploaded file, in submission
 *  order. {@code verification} is only present when {@code matchStatus} is {@code MATCHED}. */
public record BulkPodUploadRowResponse(
        String filename,
        String matchStatus,
        String message,
        String detectedShipmentNumber,
        String detectedAwb,
        UUID shipmentId,
        String shipmentNumber,
        String trackingNumber,
        PodVerificationResponse verification) {
}
