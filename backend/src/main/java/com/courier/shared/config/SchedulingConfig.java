package com.courier.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} methods — first consumer is {@code
 * com.courier.modules.support.application.ShipmentSlaSweepJob}. Spring's default
 * single-threaded scheduler is fine here: the only scheduled job today runs hourly and
 * loops companies internally rather than needing concurrent job execution.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
