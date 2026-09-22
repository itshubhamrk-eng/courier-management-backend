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
     * A reviewer's decision on a {@code PENDING} verification — approve moves it to
     * {@code PASS}, reject moves it to {@code FAIL}. Stamps {@code reviewedBy}/{@code
     * reviewedAt}.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the verification is not
     *         currently {@code PENDING} — same "illegal transition" shape every other
     *         module's lifecycle actions use
     */
    PodVerification review(UUID shipmentId, ReviewPodCommand command);

    /**
     * Company-level POD upload — no branch/delivery-assignment context needed, unlike
     * {@link #verify}. On direct user request: {@code COMPANY_ADMIN} can upload a POD for
     * any of the company's own shipments this way, and it is always auto-approved (no AI
     * call, no {@code PENDING} step) — the company vouching for it directly.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the shipment is not
     *         {@code OUT_FOR_DELIVERY}/{@code DELIVERED}, or the photo is missing/unreadable
     */
    PodVerification uploadByCompany(UUID shipmentId, CompanyUploadPodCommand command);

    /**
     * Bulk POD Upload — a batch of scanned/collected POD photos with no shipment picked in
     * advance. For each photo: runs the AI provider once to actually read the shipment/AWB
     * number off the image content (real OCR/vision, the same {@link
     * com.courier.modules.pod.application.provider.PodVerificationProvider} used by {@link
     * #verify}, never a structural-only check), matches it against this company's own
     * shipments via {@code ShipmentService.bulkTrack} (same lookup the Bulk Shipment
     * Tracking report uses), and — on exactly one match — persists a {@code PENDING} {@code
     * pod_verification} row exactly like {@link #verify} does, scored for real, a human
     * still makes the PASS/FAIL call via {@link #review}. A photo whose number can't be read,
     * matches no shipment, or matches more than one is reported back unmatched rather than
     * guessed at or silently dropped.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the batch is empty or over
     *         the per-call limit
     */
    java.util.List<BulkUploadPodOutcome> bulkUpload(java.util.List<BulkUploadPodItem> items);

    record CompanyUploadPodCommand(
            byte[] photoContent, String photoFilename, String photoContentType,
            byte[] signatureContent, String signatureFilename, String signatureContentType,
            String receiverName,
            /** Paper-register fields — see {@link com.courier.modules.pod.domain.PodEntryStatus}.
             *  All optional; a caller that doesn't collect them (e.g. a future non-register
             *  upload path) simply passes nulls. */
            java.time.LocalDate deliveryDate, String deliveredBy,
            java.time.LocalDate podDate, java.time.LocalTime podTime,
            com.courier.modules.pod.domain.PodEntryStatus entryStatus, String remark) {
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

    record BulkUploadPodItem(byte[] photoContent, String photoFilename, String photoContentType) {
    }

    enum BulkUploadPodMatchStatus {
        /** Matched exactly one shipment and a new PENDING pod_verification row was created. */
        MATCHED,
        /** Nothing legible/known in the image resolved to any of this company's shipments. */
        NO_MATCH,
        /** The image's own candidate identifiers (detected number/AWB, QR) resolved to more
         *  than one distinct shipment — refused rather than guessed. */
        AMBIGUOUS,
        /** Matched a shipment, but it is not OUT_FOR_DELIVERY/DELIVERED — same status gate
         *  {@link #uploadByCompany} applies. */
        INVALID_STATUS,
        /** The file itself was empty/unreadable. */
        ERROR
    }

    /** One outcome row per uploaded file, in submission order — {@code verification} is only
     *  non-null when {@code matchStatus} is {@code MATCHED}. */
    record BulkUploadPodOutcome(
            String filename,
            BulkUploadPodMatchStatus matchStatus,
            String message,
            String detectedShipmentNumber,
            String detectedAwb,
            UUID shipmentId,
            String shipmentNumber,
            String trackingNumber,
            PodVerification verification) {
    }
}
