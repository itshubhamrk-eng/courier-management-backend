package com.courier.modules.pod.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PodVerificationResponse(
        UUID id,
        UUID shipmentId,
        String shipmentNumber,
        String trackingNumber,
        UUID podDocumentId,
        String photoUrl,
        String signatureUrl,
        String verificationStatus,
        int verificationScore,
        List<String> verificationReasons,
        String detectedReceiverName,
        String detectedAwb,
        String detectedDate,
        boolean signatureDetected,
        String imageQuality,
        String aiProvider,
        String aiModel,
        Instant verifiedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewRemarks) {
}
