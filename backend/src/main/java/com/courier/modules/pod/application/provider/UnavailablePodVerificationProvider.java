package com.courier.modules.pod.application.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stands in for an unconfigured/unreachable AI vendor when {@code pod.ai.enabled=false} —
 * lets "AI provider unavailable -> manual review" (the brief's own explicit rule) be
 * exercised for real, the same way an unset {@code FileStoragePort}/{@code PaymentGatewayPort}
 * fails closed elsewhere in this codebase rather than silently pretending to succeed.
 */
@Service
@ConditionalOnProperty(prefix = "pod.ai", name = "enabled", havingValue = "false")
public class UnavailablePodVerificationProvider implements PodVerificationProvider {

    @Override
    public String providerName() {
        return "unavailable";
    }

    @Override
    public String modelName() {
        return "n/a";
    }

    @Override
    public PodAnalysisResult analyze(PodAnalysisRequest request) {
        throw new PodProviderUnavailableException("POD AI verification is disabled (pod.ai.enabled=false).");
    }
}
