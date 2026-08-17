package com.courier.modules.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    @Query("select t from Ticket t where t.id = :id and t.companyId = :companyId")
    Optional<Ticket> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select t.status as status, count(t) as total from Ticket t where t.companyId = :companyId group by t.status")
    List<StatusCount> countByStatus(@Param("companyId") UUID companyId);

    @Query("select t.priority as priority, count(t) as total from Ticket t where t.companyId = :companyId group by t.priority")
    List<PriorityCount> countByPriority(@Param("companyId") UUID companyId);

    @Query("select t.categoryId as categoryId, count(t) as total from Ticket t where t.companyId = :companyId group by t.categoryId")
    List<CategoryCount> countByCategory(@Param("companyId") UUID companyId);

    @Query("select t.relatedBranchId as branchId, count(t) as total from Ticket t "
            + "where t.companyId = :companyId and t.relatedBranchId is not null group by t.relatedBranchId")
    List<BranchCount> countByBranch(@Param("companyId") UUID companyId);

    @Query("select t.assigneeUserId as agentId, count(t) as total from Ticket t "
            + "where t.companyId = :companyId and t.assigneeUserId is not null group by t.assigneeUserId")
    List<AgentCount> countByAgent(@Param("companyId") UUID companyId);

    @Query("select count(t) from Ticket t where t.companyId = :companyId")
    long countAll(@Param("companyId") UUID companyId);

    /** SUPER_ADMIN only — every tenant, no company predicate. */
    @Query("select t.companyId as tenantId, count(t) as total from Ticket t group by t.companyId")
    List<TenantCount> countByTenantAll();

    @Query("select count(t) from Ticket t where t.companyId = :companyId and t.resolvedAt >= :since")
    long countResolvedSince(@Param("companyId") UUID companyId, @Param("since") Instant since);

    @Query("select count(t) from Ticket t where t.companyId = :companyId and t.closedAt >= :since")
    long countClosedSince(@Param("companyId") UUID companyId, @Param("since") Instant since);

    @Query("select t from Ticket t where t.companyId = :companyId and t.createdAt >= :since")
    List<Ticket> findCreatedSince(@Param("companyId") UUID companyId, @Param("since") Instant since);

    /** Every ticket still open (not RESOLVED/CLOSED) — the SLA-classification population. */
    @Query("select t from Ticket t where t.companyId = :companyId "
            + "and t.status not in (com.courier.modules.support.domain.TicketStatus.RESOLVED, "
            + "com.courier.modules.support.domain.TicketStatus.CLOSED)")
    List<Ticket> findOpenTickets(@Param("companyId") UUID companyId);

    /** SUPER_ADMIN sweep: every ticket across every company with an SLA still to watch,
     *  regardless of the caller's own company scope — see {@code TicketSlaSweepJob}. */
    @Query("select t from Ticket t where t.status not in (com.courier.modules.support.domain.TicketStatus.RESOLVED, "
            + "com.courier.modules.support.domain.TicketStatus.CLOSED) "
            + "and t.slaResolutionDueAt is not null "
            + "and (t.slaWarningNotified = false or t.slaBreachNotified = false)")
    List<Ticket> findAllOpenWithPendingSla();

    interface StatusCount {
        TicketStatus getStatus();
        long getTotal();
    }

    interface PriorityCount {
        TicketPriority getPriority();
        long getTotal();
    }

    interface CategoryCount {
        UUID getCategoryId();
        long getTotal();
    }

    interface BranchCount {
        UUID getBranchId();
        long getTotal();
    }

    interface AgentCount {
        UUID getAgentId();
        long getTotal();
    }

    interface TenantCount {
        UUID getTenantId();
        long getTotal();
    }
}
