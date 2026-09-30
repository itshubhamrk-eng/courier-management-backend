package com.courier.modules.shipment.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Throttles verification attempts against {@code /api/v1/track/{number}/verify}. The
 * receiver-phone-last-4 check that guards sender/receiver address, POD and ticket detail on
 * the public tracking page is a 4-digit code — only 10,000 combinations — so without this,
 * anyone who already knows a shipment or tracking number (they are sequential integers; see
 * {@code PublicTrackingServiceImpl}) could brute-force it in a small number of requests.
 *
 * <p>In-memory and keyed by shipment number rather than caller IP: the goal is bounding how
 * many guesses one specific shipment's code can absorb regardless of how many source
 * addresses they come from, not tracking any one caller. Single-instance deployment (see
 * {@code MEMORY/AI_CONTEXT.md}) — a multi-instance rollout would need this backed by
 * something shared (Redis) instead, same caveat as any other in-process state here.
 */
@Component
public class PublicTrackVerificationLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    /**
     * @throws com.courier.shared.exception.BusinessRuleException too many recent failed
     *         attempts for this key
     */
    public void checkAllowed(String key) {
        Deque<Instant> window = attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minus(WINDOW);
        window.removeIf(t -> t.isBefore(cutoff));

        if (window.size() >= MAX_ATTEMPTS) {
            throw new com.courier.shared.exception.BusinessRuleException(
                    com.courier.shared.exception.ErrorCode.RATE_LIMIT_EXCEEDED,
                    "Too many verification attempts. Please try again later.");
        }
    }

    /** Call once per failed attempt, after {@link #checkAllowed} passes. */
    public void recordFailure(String key) {
        attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>()).addLast(Instant.now());
    }

    /** Call on a successful verification, so a legitimate customer isn't penalised for
     *  earlier typos once they get the code right. */
    public void reset(String key) {
        attempts.remove(key);
    }
}
