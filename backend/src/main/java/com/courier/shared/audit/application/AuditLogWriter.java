package com.courier.shared.audit.application;

import com.courier.shared.audit.domain.AuditLog;
import com.courier.shared.audit.domain.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists audit entries off the request thread.
 *
 * <p>A separate bean from {@link AuditService} on purpose: {@code @Async} and
 * {@code @Transactional} are proxy-based, and a self-invocation inside a single bean
 * bypasses the proxy entirely — the method would silently run synchronously in the
 * caller's transaction, which is exactly what the annotations exist to prevent.
 * Crossing a bean boundary is what makes them take effect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository repository;

    /**
     * {@code REQUIRES_NEW} so the record commits independently of the business
     * transaction: a rolled-back or rejected action must still leave a trail.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog entry) {
        try {
            repository.save(entry);
        } catch (Exception e) {
            // Never propagate: the caller has already returned, and an audit failure
            // must not become an application failure. The entry is preserved in the
            // application log as a fallback trail.
            log.error("Failed to persist audit log: action={} company={} actor={} requestId={}",
                    entry.getAction(), entry.getCompanyId(), entry.getActorId(), entry.getRequestId(), e);
        }
    }
}
