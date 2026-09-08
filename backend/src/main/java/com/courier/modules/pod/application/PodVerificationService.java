package com.courier.modules.pod.application;

import com.courier.modules.pod.domain.PodVerification;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
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

    /** Latest verification per shipment, for these shipment ids — batch form of
     *  {@link #getLatest}, for the POD Review table (every delivered shipment, whether or
     *  not it ever had a POD run). A shipment missing from the returned map has none. */
    Map<UUID, PodVerification> latestByShipmentIds(Collection<UUID> shipmentIds);

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

    /**
     * Company-level POD upload — no branch/delivery-assignment context needed, unlike
     * {@link #verify}. On direct user request: {@code COMPANY_ADMIN} can upload a POD for
     * any of the company's own shipments this way, and it is always auto-approved (no AI
     * call, no {@code REVIEW} step) — the company vouching for it directly.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the shipment is not
     *         {@code OUT_FOR_DELIVERY}/{@code DELIVERED}, or the photo is missing/unreadable
     */
    PodVerification uploadByCompany(UUID shipmentId, CompanyUploadPodCommand command);

    record CompanyUploadPodCommand(
            byte[] photoContent, String photoFilename, String photoContentType,
            byte[] signatureContent, String signatureFilename, String signatureContentType,
            String receiverName) {
    }

    record VerifyPodCommand(
            byte[] photoContent, String photoFilename, String photoContentType,
            byte[] signatureContent, String signatureFilename, String signatureContentType,
            String receiverName, String awbNumber, String shipmentNumberClaim,
            Instant deliveryDateTime,
            /** LR/tracking number decoded live off the label's QR by the delivery app's own
             *  camera, before this upload — a real independent cross-check. Null when the
             *  delivery app didn't scan (or couldn't); {@link PodVerificationServiceImpl}
             *  falls back to decoding the QR out of the uploaded photo itself when this is
             *  blank, so the cross-check still runs without a dedicated scan step. */
            String qrScanValue) {
    }

    record ReviewPodCommand(boolean approve, String remarks) {
    }
}
