package com.courier.modules.pod.application.provider;

import java.util.List;

/**
 * The hard-fail rules that don't depend on which {@link PodVerificationProvider} scored the
 * image — an AWB/shipment-number or QR value that disagrees with this platform's own DB
 * record, or a POD photo hash already used on a different shipment, is this module's own
 * policy, not a vendor's. Every provider (the deterministic {@link HeuristicPodVerificationProvider}
 * and any real vision/OCR provider alike) applies these on top of its own raw score, so a
 * vision call scoring an unrelated-but-well-lit photo highly still can't out-vote a ground-truth
 * mismatch the platform already knows about.
 */
final class PodGroundTruthRules {

    private PodGroundTruthRules() {
    }

    record Outcome(int score, boolean mustReview) {
    }

    /** Applies the AWB/QR mismatch hard-zero and the duplicate-photo-hash penalty on top of
     *  {@code score}, appending human-readable reasons to the caller's mutable {@code reasons}
     *  list. Returns the adjusted score and whether the result must be routed to REVIEW
     *  regardless of score. */
    static Outcome applyHardFailRules(int score, List<String> reasons, PodAnalysisRequest request) {
        boolean awbMismatch = isMismatch(request.claimedAwb(), request.shipmentActualAwb())
                || isMismatch(request.claimedShipmentNumber(), request.shipmentActualNumber());
        if (awbMismatch) {
            // Hard fail, not a point deduction: ground truth already lives in this platform's
            // own DB record (shipmentActualAwb/shipmentActualNumber), so a mismatch here is not
            // a quality signal to weigh against others — it means this POD was captured for a
            // different shipment. Zeroing the score keeps it out of both PASS and REVIEW no
            // matter how clean the rest of the photo looks; a re-upload against the right
            // shipment is the only way forward, never a manual approve.
            score = 0;
            reasons.add("Provided AWB/shipment number does not match this shipment's own record.");
        }

        // Same hard-fail rule for the label's own QR code — checked independently of
        // claimedAwb/claimedShipmentNumber (those can be a self-echo of the shipment record
        // the delivery app already has open; the QR comes off the physical parcel). Either
        // source disagreeing with this platform's ground truth is a hard reject on its own.
        boolean qrMismatch = isMismatch(request.qrScanValue(), request.shipmentActualAwb())
                && isMismatch(request.qrScanValue(), request.shipmentActualNumber());
        if (qrMismatch) {
            score = 0;
            reasons.add("Scanned QR code does not match this shipment's own record.");
        }

        boolean mustReview = false;
        if (request.duplicateSuspectedByHash()) {
            score -= 50;
            reasons.add("This POD photo matches one already used on a different shipment — "
                    + "possible duplicate submission.");
            mustReview = true;
        }

        return new Outcome(Math.max(0, Math.min(100, score)), mustReview);
    }

    private static boolean isMismatch(String claimed, String actual) {
        if (claimed == null || claimed.isBlank() || actual == null || actual.isBlank()) {
            return false;
        }
        return !claimed.trim().equalsIgnoreCase(actual.trim());
    }
}
