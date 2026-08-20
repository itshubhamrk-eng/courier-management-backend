package com.courier.modules.pod.application;

import com.courier.modules.pod.domain.PodVerification;

import java.time.Instant;
import java.util.UUID;

/**
 * POD Auto Verification use cases. AI never updates a shipment's status directly — this
 * service only ever writes {@code pod_verification} rows; the existing {@code
 * ShipmentService.deliver()} business rules stay the sole path to {@code DELIVERED}. See
 * {@code MEMORY/modules/pod-verification.md}.
 */
public interface PodVerificationService {

    /**
     * Runs AI verification against a freshly-captured POD photo (required) and optional
     * signature. Persists both as {@code ShipmentAsset} rows immediately (via {@code
     * ShipmentService.attachPodAsset}, independent of whether delivery is ever completed
     * from this capture) and writes one new {@code pod_verification} row.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the shipment is not
     *         {@code OUT_FOR_DELIVERY}, or the photo is missing/unreadable as a file
     */
    PodVerification verify(UUID shipmentId, VerifyPodCommand command);

    /** The most recent verification run for a shipment. */
    PodVerification getLatest(UUID shipmentId);

    /** Every verification currently awaiting a human decision — the Manual Review screen's
     *  worklist, oldest first. */
    java.util.List<PodVerification> listPendingReview();

    /**
     * A reviewer's decision on a {@code REVIEW}-status verification — approve moves it to
     * {@code PASS}, reject moves it to {@code FAIL}. Stamps {@code reviewedBy}/{@code
     * reviewedAt}.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the verification is not
     *         currently {@code REVIEW} — same "illegal transition" shape every other
     *         module's lifecycle actions use
     */
    PodVerification review(UUID shipmentId, ReviewPodCommand command);

    record VerifyPodCommand(
            byte[] photoContent, String photoFilename, String photoContentType,
            byte[] signatureContent, String signatureFilename, String signatureContentType,
            String receiverName, String awbNumber, String shipmentNumberClaim,
            Instant deliveryDateTime) {
    }

    record ReviewPodCommand(boolean approve, String remarks) {
    }
}
