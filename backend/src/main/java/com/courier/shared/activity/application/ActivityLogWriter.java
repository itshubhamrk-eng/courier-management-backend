package com.courier.shared.activity.application;

import com.courier.shared.activity.domain.ActivityLog;
import com.courier.shared.activity.domain.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists an activity entry off the request thread. Mirrors
 * {@code shared.audit.application.AuditLogWriter} exactly, including the separate-bean
 * reasoning ({@code @Async}/{@code @Transactional} are proxy-based; a self-invocation
 * would bypass both) and reuses its {@code auditExecutor} — one bounded pool for both
 * append-only trails is simpler than two pools tuned identically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLogWriter {

    private final ActivityLogRepository repository;

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(ActivityLog entry) {
        try {
            repository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist activity log: module={} action={} company={} user={}",
                    entry.getModule(), entry.getAction(), entry.getCompanyId(), entry.getUserId(), e);
        }
    }
}
