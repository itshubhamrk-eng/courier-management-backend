package com.courier.modules.pod.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of the POD Review table — every DELIVERED shipment, whether or not a POD
 * verification ever ran against it. Every {@code pod*}/{@code verification*} field is
 * {@code null} for a shipment that has none (delivered before POD Auto Verification existed,
 * or via a path that skipped POD capture), never a zero row.
 */
public record DeliveredShipmentPodResponse(
        UUID shipmentId,
        String shipmentNumber,
        String trackingNumber,
        UUID bookingBranchId,
        UUID deliveryBranchId,
        String receiverName,
        Instant receivedAt,
        Instant deliveredAt,
        UUID podVerificationId,
        String verificationStatus,
        Integer verificationScore,
        List<String> verificationReasons,
        String photoUrl,
        String signatureUrl,
        String aiProvider,
        String aiModel) {
}
