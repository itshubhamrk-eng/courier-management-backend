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

    /**
     * Scanner input: distinct (session, browser, os) seen in {@code [from, to)}. Rows with no
     * resolved session or user are skipped — there is nothing to compare them against.
     * Cross-company on purpose (the scanner runs with no company bound).
     */
    @Query("""
           select new com.courier.shared.activity.domain.SessionFingerprint(
                      a.companyId, a.userId, a.sessionId, a.browser, a.os)
             from ActivityLog a
            where a.sessionId is not null and a.userId is not null
              and a.occurredAt >= :from and a.occurredAt < :to
            group by a.companyId, a.userId, a.sessionId, a.browser, a.os
           """)
    List<SessionFingerprint> findSessionFingerprints(@Param("from") Instant from, @Param("to") Instant to);

    /** Scanner input: sessions whose activity in {@code [from, to)} came from 2+ distinct IPs. */
    @Query("""
           select new com.courier.shared.activity.domain.SessionIpSummary(
                      a.companyId, a.userId, a.sessionId, count(distinct a.ipAddress))
             from ActivityLog a
            where a.sessionId is not null and a.userId is not null and a.ipAddress is not null
              and a.occurredAt >= :from and a.occurredAt < :to
            group by a.companyId, a.userId, a.sessionId
           having count(distinct a.ipAddress) >= 2
           """)
    List<SessionIpSummary> findMultiIpSessions(@Param("from") Instant from, @Param("to") Instant to);

    /** A few of the IPs behind a multi-IP session, for incident evidence; bound with {@code pageable}. */
    @Query("""
           select distinct a.ipAddress from ActivityLog a
            where a.sessionId = :sessionId and a.ipAddress is not null
              and a.occurredAt >= :from and a.occurredAt < :to
            order by a.ipAddress
           """)
    List<String> findDistinctIps(@Param("sessionId") UUID sessionId, @Param("from") Instant from,
                                 @Param("to") Instant to, Pageable pageable);

    /** Supports the retention job noted alongside {@code AuditLogRepository}'s own. */
    long deleteByOccurredAtBefore(Instant cutoff);

    /** Distinct values the Activity Log screen's Module/Action/Entity Type filters offer as
     *  a dropdown, rather than free text — sourced from what has actually been logged
     *  rather than a hardcoded list that would drift from {@code ActivityLoggingFilter}'s
     *  own inference (module is the URL's first segment, action is verb-or-keyword-derived,
     *  both effectively open-ended). {@code companyId} null means every company — the
     *  platform-tier caller's own case, guarded the same way {@code ActivityLogService}
     *  guards every other read here. */
    @Query("""
           select distinct a.module from ActivityLog a
            where (:companyId is null or a.companyId = :companyId) and a.module is not null
            order by a.module
           """)
    List<String> findDistinctModules(@Param("companyId") UUID companyId);

    @Query("""
           select distinct a.action from ActivityLog a
            where (:companyId is null or a.companyId = :companyId) and a.action is not null
            order by a.action
           """)
    List<String> findDistinctActions(@Param("companyId") UUID companyId);

    @Query("""
           select distinct a.entityType from ActivityLog a
            where (:companyId is null or a.companyId = :companyId) and a.entityType is not null
            order by a.entityType
           """)
    List<String> findDistinctEntityTypes(@Param("companyId") UUID companyId);
}
