package com.courier.shared.activity.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository
        extends JpaRepository<ActivityLog, UUID>, JpaSpecificationExecutor<ActivityLog> {

    /** Recent activities for the User Activity screen — bounded by {@code pageable}'s size. */
    Page<ActivityLog> findByCompanyIdAndUserIdOrderByOccurredAtDesc(UUID companyId, UUID userId, Pageable pageable);

    /**
     * Distinct modules a user has touched — "modules accessed" on the same screen.
     * A derived query name cannot express a scalar projection (the segment between
     * "find" and "By" is only ever a readability placeholder, never a field selector —
     * discovered by this exact query throwing {@code QueryTypeMismatchException} against
     * a real database, not by any unit test mocking the repository), hence the explicit
     * JPQL.
     */
    @Query("""
           select distinct a.module from ActivityLog a
            where a.companyId = :companyId and a.userId = :userId and a.module is not null
           """)
    List<String> findDistinctModulesFor(@Param("companyId") UUID companyId, @Param("userId") UUID userId);

    /** Supports the retention job noted alongside {@code AuditLogRepository}'s own. */
    long deleteByOccurredAtBefore(Instant cutoff);
}
