package com.courier.modules.pod.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment-wide POD Auto Verification settings, bound from {@code pod.verification.*}.
 * Every delivery-app upload lands {@code PENDING} regardless of AI score (see
 * {@link com.courier.modules.pod.domain.PodVerificationStatus}) — {@link
 * #manualReviewThreshold} only supplies the fallback score stamped on a run when the AI
 * provider itself is unavailable.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pod.verification")
public class PodVerificationProperties {

    /** Fallback score recorded when the AI provider is unavailable — informational only,
     *  never affects the resulting status. */
    private int manualReviewThreshold = 60;
}
