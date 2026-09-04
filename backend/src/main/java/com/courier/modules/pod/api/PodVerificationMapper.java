package com.courier.modules.pod.api;

import com.courier.modules.pod.api.dto.DeliveredShipmentPodResponse;
import com.courier.modules.pod.api.dto.PodVerificationResponse;
import com.courier.modules.pod.domain.PodVerification;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentAsset;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

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

    /** One POD Review table row — {@code v} and {@code deliveredAt} are null for a delivered
     *  shipment with no POD verification ever run. */
    public DeliveredShipmentPodResponse toDeliveredRow(Shipment s, PodVerification v, Instant deliveredAt,
                                                         List<ShipmentAsset> podAssets) {
        return new DeliveredShipmentPodResponse(
                s.getId(), s.getShipmentNumber(), s.getTrackingNumber(), s.getDeliveryBranchId(),
                s.getReceiverName(), deliveredAt,
                v == null ? null : v.getId(),
                v == null ? null : v.getVerificationStatus().name(),
                v == null ? null : v.getVerificationScore(),
                v == null ? null : v.reasons(),
                latestAssetUrl(podAssets, "PHOTO"), latestAssetUrl(podAssets, "SIGNATURE"),
                v == null ? null : v.getAiProvider(), v == null ? null : v.getAiModel());
    }

    private static String latestAssetUrl(List<ShipmentAsset> assets, String kind) {
        return assets.stream().filter(a -> kind.equals(a.getKind()))
                .findFirst().map(ShipmentAsset::getAssetUrl).orElse(null);
    }
}
