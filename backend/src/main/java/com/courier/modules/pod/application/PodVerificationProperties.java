package com.courier.modules.pod.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment-wide POD Auto Verification thresholds, bound from {@code pod.verification.*} —
 * the brief's own {@code POD_AUTO_VERIFY_THRESHOLD}/{@code POD_MANUAL_REVIEW_THRESHOLD},
 * never hardcoded in {@code PodVerificationServiceImpl}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pod.verification")
public class PodVerificationProperties {

    /** Score at or above this is {@code PASS}. */
    private int autoVerifyThreshold = 85;

    /** Score at or above this (and below {@link #autoVerifyThreshold}) is {@code REVIEW};
     *  below it is {@code FAIL}. */
    private int manualReviewThreshold = 60;
}
