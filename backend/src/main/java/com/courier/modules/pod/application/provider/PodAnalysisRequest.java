package com.courier.modules.pod.application.provider;

import java.time.Instant;

/**
 * Everything a {@link PodVerificationProvider} needs to score one POD capture. {@code
 * claimedAwb}/{@code claimedShipmentNumber} are the values the delivery user (or their app)
 * asserts this POD belongs to — {@code shipmentActualAwb}/{@code shipmentActualNumber} are
 * this platform's own ground truth for the shipment being delivered, already known
 * server-side with no OCR required. A provider that cannot read text out of the image at all
 * (this codebase's own {@code HeuristicPodVerificationProvider}) can still meaningfully flag
 * a mismatch between what was claimed and what the system already knows.
 *
 * <p>{@code qrScanValue} is a third, independent identifier — the LR/tracking number decoded
 * from the physical label's own QR code, either scanned live by the delivery app's camera
 * before capture, or (when no live scan happened) decoded server-side from the POD photo
 * itself via {@link PodQrDecoder}, whichever resolved first. Unlike {@code claimedAwb} (which
 * the delivery app may simply echo back from the shipment record it already opened, proving
 * nothing), this value comes off the physical parcel — a real independent cross-check, not a
 * self-referential one.
 */
public record PodAnalysisRequest(
        byte[] photoBytes,
        String photoContentType,
        byte[] signatureBytes,
        String receiverName,
        String claimedAwb,
        String claimedShipmentNumber,
        String shipmentActualAwb,
        String shipmentActualNumber,
        Instant deliveryDateTime,
        boolean duplicateSuspectedByHash,
        String qrScanValue) {
}
