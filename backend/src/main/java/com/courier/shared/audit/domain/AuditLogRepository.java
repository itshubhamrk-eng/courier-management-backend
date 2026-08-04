package com.courier.shared.audit.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit logs are written far more often than they are read, and read only by
 * administrators. Queries are therefore deliberately few and index-aligned.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByCompanyIdOrderByOccurredAtDesc(UUID companyId, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndActionOrderByOccurredAtDesc(UUID companyId,
                                                                AuditAction action,
                                                                Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType,
                                                                    UUID entityId,
                                                                    Pageable pageable);

    Page<AuditLog> findByActorIdOrderByOccurredAtDesc(UUID actorId, Pageable pageable);

    /** Supports the retention job noted in {@code MEMORY/BACKLOG.md}. */
    long deleteByOccurredAtBefore(Instant cutoff);
}
