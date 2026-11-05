package com.courier.modules.security.application.scan;

import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityIncidentService;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.shared.company.CompanyContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Periodically runs every {@link ScanRule} over a sliding window and raises what they find.
 *
 * <p>Window: each run computes {@code to = now - scanLag} and scans
 * {@code [to - scanWindow, to)}. The window is longer than the run interval, so consecutive
 * runs overlap: a rule that failed, or a row that committed late, is picked up again by the
 * next runs, and {@code SecurityIncidentService#raise} dedups so re-detecting is harmless.
 * The cursor row only records the last {@code to} and always advances; a failing rule can
 * never freeze or widen anything. The very first run (no cursor row) records its cursor and
 * scans nothing, so history from before the feature existed is never swept.
 *
 * <p>Deliberately NOT {@code @Transactional}: {@code raise} takes a row lock on the target
 * user in its own REQUIRES_NEW transaction and requires its caller to hold no uncommitted
 * writes or locks on that user. Each repository read runs in its own short transaction. Like
 * {@code FollowUpSweepJob} it runs with no {@code CompanyContext} bound (cleared defensively
 * at the start, since the scheduler thread is shared), so the Hibernate company filter is off
 * and reads are cross-company.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "courier.security", name = "scan-enabled", havingValue = "true", matchIfMissing = true)
public class SecurityScanJob {

    private final ScanCursorRepository cursorRepository;
    private final List<ScanRule> rules;
    private final SecurityIncidentService incidentService;
    private final SecurityProperties properties;
    private final Clock clock;

    @Autowired
    public SecurityScanJob(ScanCursorRepository cursorRepository, List<ScanRule> rules,
                           SecurityIncidentService incidentService, SecurityProperties properties) {
        this(cursorRepository, rules, incidentService, properties, Clock.systemUTC());
    }

    SecurityScanJob(ScanCursorRepository cursorRepository, List<ScanRule> rules,
                    SecurityIncidentService incidentService, SecurityProperties properties, Clock clock) {
        this.cursorRepository = cursorRepository;
        this.rules = rules;
        this.incidentService = incidentService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${courier.security.scan-interval:PT2M}")
    public void scan() {
        CompanyContext.clear();
        Instant to = clock.instant().minus(properties.getScanLag());
        Instant from = to.minus(properties.getScanWindow());

        Optional<ScanCursor> existing = cursorRepository.findById(ScanCursor.SINGLETON_ID);
        if (existing.isEmpty()) {
            try {
                cursorRepository.save(new ScanCursor(ScanCursor.SINGLETON_ID, to));
                log.info("Security scan cursor initialised at {}; history is not scanned", to);
            } catch (DataIntegrityViolationException e) {
                log.debug("Security scan cursor was initialised concurrently; skipping this run");
            }
            return;
        }

        for (ScanRule rule : rules) {
            List<IncidentRequest> requests;
            try {
                requests = rule.scan(from, to);
            } catch (RuntimeException e) {
                log.error("Security scan rule {} failed for window [{}, {}); the overlap retries it",
                        rule.code(), from, to, e);
                continue;
            }
            for (IncidentRequest request : requests) {
                try {
                    incidentService.raise(request);
                } catch (RuntimeException e) {
                    log.warn("Could not raise {} incident for user {}; the overlap retries it",
                            request.rule(), request.userId(), e);
                }
            }
        }

        ScanCursor cursor = existing.get();
        cursor.setLastScannedAt(to);
        cursorRepository.save(cursor);
    }
}
