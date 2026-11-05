package com.courier.shared.activity.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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
     * Scanner input (BULK_ACTIVITY): successful rows in {@code [from, to)} whose action is one
     * of {@code actions}, per user and (module, action). Cross-company on purpose; uses
     * {@code idx_activity_logs_action_time (action, occurred_at)}.
     */
    @Query("""
           select new com.courier.shared.activity.domain.UserActionCount(
                      a.companyId, a.userId, a.module, a.action, count(a))
             from ActivityLog a
            where a.action in :actions
              and a.occurredAt >= :from and a.occurredAt < :to
              and a.status = com.courier.shared.activity.domain.ActivityStatus.SUCCESS
              and a.userId is not null
            group by a.companyId, a.userId, a.module, a.action
           """)
    List<UserActionCount> findSuccessCountsByAction(@Param("from") Instant from, @Param("to") Instant to,
                                                    @Param("actions") Collection<String> actions);

    /**
     * Scanner input (BULK_ACTIVITY rate edits): successful rows in {@code [from, to)} in one of
     * {@code modules} with one of {@code actions}, skipping rows whose endpoint template is in
     * {@code excludedEndpoints} (exact match), per user and (module, action). Cross-company
     * on purpose; uses {@code idx_activity_logs_module_time (module, occurred_at)}.
     */
    @Query("""
           select new com.courier.shared.activity.domain.UserActionCount(
                      a.companyId, a.userId, a.module, a.action, count(a))
             from ActivityLog a
            where a.module in :modules
              and a.occurredAt >= :from and a.occurredAt < :to
              and a.action in :actions
              and a.status = com.courier.shared.activity.domain.ActivityStatus.SUCCESS
              and a.userId is not null
              and (a.apiEndpoint is null or a.apiEndpoint not in :excludedEndpoints)
            group by a.companyId, a.userId, a.module, a.action
           """)
    List<UserActionCount> findSuccessCountsByModuleAndAction(@Param("from") Instant from, @Param("to") Instant to,
                                                             @Param("modules") Collection<String> modules,
                                                             @Param("actions") Collection<String> actions,
                                                             @Param("excludedEndpoints")
                                                             Collection<String> excludedEndpoints);

    /**
     * Scanner input (ACCESS_DENIED_BURST): FAILURE rows in {@code [from, to)} whose stored
     * {@code error_message} equals {@code errorMessage} exactly, per user and (method,
     * {@code api_endpoint}). {@code api_endpoint} is the route template for requests that reached
     * a handler, but the raw URI for ones the security chain rejected before handler mapping. Cross-company on purpose; uses {@code idx_activity_logs_status_time} (V143).
     */
    @Query("""
           select new com.courier.shared.activity.domain.UserEndpointCount(
                      a.companyId, a.userId, a.requestMethod, a.apiEndpoint, count(a))
             from ActivityLog a
            where a.status = com.courier.shared.activity.domain.ActivityStatus.FAILURE
              and a.occurredAt >= :from and a.occurredAt < :to
              and a.errorMessage = :errorMessage
              and a.userId is not null
            group by a.companyId, a.userId, a.requestMethod, a.apiEndpoint
           """)
    List<UserEndpointCount> findFailureCountsByErrorMessage(@Param("from") Instant from, @Param("to") Instant to,
                                                            @Param("errorMessage") String errorMessage);

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
