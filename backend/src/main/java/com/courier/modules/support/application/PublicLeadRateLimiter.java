package com.courier.modules.support.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Throttles {@code POST /api/v1/leads} by caller IP — an unauthenticated write endpoint
 * with no CAPTCHA, so without this a bot can flood the ticket queue in a tight loop. Same
 * shape as {@code shipment.application.PublicTrackVerificationLimiter}, kept as its own
 * small class rather than a shared generic utility for the same reason that one is: each
 * public endpoint's throttling is a one-line policy decision, not a service worth a seam.
 */
@Component
public class PublicLeadRateLimiter {

    private static final int MAX_SUBMISSIONS = 1;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Deque<Instant>> submissions = new ConcurrentHashMap<>();

    /**
     * @throws com.courier.shared.exception.BusinessRuleException too many recent
     *         submissions from this IP
     */
    public void checkAllowed(String clientIp) {
        String key = clientIp == null ? "unknown" : clientIp;
        Deque<Instant> window = submissions.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minus(WINDOW);
        window.removeIf(t -> t.isBefore(cutoff));

        if (window.size() >= MAX_SUBMISSIONS) {
            throw new com.courier.shared.exception.BusinessRuleException(
                    com.courier.shared.exception.ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many requests. Please try again later.");
        }
        window.addLast(Instant.now());
    }
}
