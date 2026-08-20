package com.courier.modules.pod.application.provider;

/**
 * The AI-analysis seam — the same split {@code PaymentGatewayPort}/{@code FileStoragePort}
 * draw for their own external dependencies. {@link com.courier.modules.pod.application
 * .PodVerificationServiceImpl} never talks to an AI vendor directly, never inspects raw AI
 * text, and never lets a provider decide a shipment's status — it only ever reads the
 * structured {@link PodAnalysisResult} this interface returns and applies this module's own
 * configurable score thresholds on top.
 *
 * <p>The only implementation shipped in this codebase, {@link HeuristicPodVerificationProvider},
 * is a deterministic local scorer — there is no AI vendor credential configured in this
 * dev environment, the same class of gap {@code NotificationPort}/SMTP and an unconfigured
 * {@code FileStoragePort} already carry. A real vision/OCR provider (AWS Rekognition/Textract,
 * Google Vision, an LLM multimodal call) is a second implementation of this interface and a
 * config change — no caller of {@link com.courier.modules.pod.application.PodVerificationService}
 * changes.
 */
public interface PodVerificationProvider {

    /** Short vendor identifier stamped onto {@code pod_verification.ai_provider}. */
    String providerName();

    /** Model/version identifier stamped onto {@code pod_verification.ai_model}. */
    String modelName();

    /**
     * Analyses one POD capture and returns a structured result. Never returns a status or
     * business decision — only signals; {@link com.courier.modules.pod.application
     * .PodVerificationServiceImpl} owns turning them into PASS/REVIEW/FAIL.
     *
     * @throws PodProviderUnavailableException the provider cannot run right now (disabled,
     *         unreachable, or the environment has none configured) — the caller must route
     *         to manual review, never assume success
     */
    PodAnalysisResult analyze(PodAnalysisRequest request);
}
