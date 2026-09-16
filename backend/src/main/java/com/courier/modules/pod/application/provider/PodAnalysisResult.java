package com.courier.modules.pod.application.provider;

import java.util.List;

/**
 * Structured, non-negotiable output of one {@link PodVerificationProvider#analyze} call.
 * Deliberately has no status/action field of its own — {@code score} plus the two safety
 * flags are shown to the human reviewer as reference only; {@code PodVerificationServiceImpl
 * .verify()} always stores the result as {@code PENDING}, a human makes the PASS/FAIL call.
 *
 * @param score                0–100, this provider's own confidence — informational
 * @param reasons               human-readable, most-significant first; never raw model text —
 *                              a provider builds these itself, never passes through anything
 *                              an end user or the AI vendor wrote free-form
 * @param signatureDetected     was a signature capture present/plausible
 * @param imageQuality          {@code GOOD}, {@code FAIR}, or {@code POOR}
 * @param detectedReceiverName  best-effort — may simply echo the claimed value for a
 *                              non-OCR provider; see the class doc on {@link PodVerificationProvider}
 * @param detectedAwb           best-effort, same caveat
 * @param detectedDate          best-effort, same caveat; a free-text field, not parsed
 * @param tamperingSuspected    a weak, honestly-labelled signal — never asserted as proof
 * @param mustReviewRegardlessOfScore a duplicate/tampering safety signal — surfaced to the
 *                              reviewer alongside {@code reasons}; every result is PENDING
 *                              regardless, so this no longer changes the resulting status
 */
public record PodAnalysisResult(
        int score,
        List<String> reasons,
        boolean signatureDetected,
        String imageQuality,
        String detectedReceiverName,
        String detectedAwb,
        String detectedDate,
        boolean tamperingSuspected,
        boolean mustReviewRegardlessOfScore) {
}
