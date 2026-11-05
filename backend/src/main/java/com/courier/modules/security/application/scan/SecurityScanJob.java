package com.courier.modules.security.application.scan;

import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityIncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Periodically runs every {@link ScanRule} over the window since the last run and raises
 * what they find.
 *
 * <p>Deliberately NOT {@code @Transactional}: {@link SecurityIncidentService#raise} takes a
 * row lock on the target user in its own REQUIRES_NEW transaction and requires its caller to
 * hold no uncommitted writes or locks on that user. Each repository read here runs in its own
 * short transaction. Like {@code FollowUpSweepJob}, it runs with no {@code CompanyContext}
 * bound, so the Hibernate company filter is not applied and reads are cross-company.
 *
 * <p>Cursor policy: the very first run only records "now" (history is never swept). A later
 * run scans {@code [cursor, now)} and advances the cursor only if every rule scanned
 * successfully, so a failed rule's window is retried (rules and {@code raise} are idempotent
 * per user). The lookback is capped at {@link #MAX_LOOKBACK} so a permanently failing rule or
 * a long outage cannot make one scan unbounded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courier.security", name = "scan-enabled", havingValue = "true", matchIfMissing = true)
public class SecurityScanJob {

    static final Duration MAX_LOOKBACK = Duration.ofHours(1);

    private final ScanCursorRepository cursorRepository;
    private final List<ScanRule> rules;
    private final SecurityIncidentService incidentService;

    @Scheduled(fixedDelayString = "${courier.security.scan-interval:PT2M}")
    public void scan() {
        Instant now = Instant.now();
        Optional<ScanCursor> existing = cursorRepository.findById(ScanCursor.SINGLETON_ID);
        if (existing.isEmpty()) {
            cursorRepository.save(new ScanCursor(ScanCursor.SINGLETON_ID, now));
            log.info("Security scan cursor initialised at {}; history is not scanned", now);
            return;
        }

        ScanCursor cursor = existing.get();
        Instant from = cursor.getLastScannedAt();
        Instant earliest = now.minus(MAX_LOOKBACK);
        if (from.isBefore(earliest)) {
            log.warn("Security scan cursor {} is older than {}; scanning from {}", from, MAX_LOOKBACK, earliest);
            from = earliest;
        }

        boolean allScanned = true;
        for (ScanRule rule : rules) {
            List<IncidentRequest> requests;
            try {
                requests = rule.scan(from, now);
            } catch (RuntimeException e) {
                allScanned = false;
                log.error("Security scan rule {} failed for window [{}, {})", rule.code(), from, now, e);
                continue;
            }
            for (IncidentRequest request : requests) {
                try {
                    incidentService.raise(request);
                } catch (RuntimeException e) {
                    log.error("Could not raise {} incident for user {}", request.rule(), request.userId(), e);
                }
            }
        }

        if (allScanned) {
            cursor.setLastScannedAt(now);
            cursorRepository.save(cursor);
        }
    }
}
