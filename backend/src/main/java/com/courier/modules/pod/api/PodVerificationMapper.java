package com.courier.modules.pod.api;

import com.courier.modules.pod.api.dto.PodVerificationResponse;
import com.courier.modules.pod.domain.PodVerification;
import com.courier.modules.shipment.domain.Shipment;
import org.springframework.stereotype.Component;

@Component
public class PodVerificationMapper {

    public PodVerificationResponse toResponse(PodVerification v, Shipment shipment,
                                               String photoUrl, String signatureUrl) {
        return new PodVerificationResponse(
                v.getId(), v.getShipmentId(),
                shipment == null ? null : shipment.getShipmentNumber(),
                shipment == null ? null : shipment.getTrackingNumber(),
                v.getPodDocumentId(), photoUrl, signatureUrl,
                v.getVerificationStatus().name(), v.getVerificationScore(), v.reasons(),
                v.getDetectedReceiverName(), v.getDetectedAwb(), v.getDetectedDate(),
                v.isSignatureDetected(), v.getImageQuality(),
                v.getAiProvider(), v.getAiModel(), v.getVerifiedAt(),
                v.getReviewedBy(), v.getReviewedAt(), v.getReviewRemarks());
    }
}
