package com.courier.modules.followup.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID>, JpaSpecificationExecutor<FollowUp> {

    @Query("select f from FollowUp f where f.id = :id and f.companyId = :companyId")
    Optional<FollowUp> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select count(f) from FollowUp f where f.companyId = :companyId and f.status not in "
            + "(com.courier.modules.followup.domain.FollowUpStatus.COMPLETED, "
            + "com.courier.modules.followup.domain.FollowUpStatus.CANCELLED) and f.dueDate < :now")
    long countOverdue(@Param("companyId") UUID companyId, @Param("now") Instant now);

    @Query("select count(f) from FollowUp f where f.companyId = :companyId and f.status not in "
            + "(com.courier.modules.followup.domain.FollowUpStatus.COMPLETED, "
            + "com.courier.modules.followup.domain.FollowUpStatus.CANCELLED) "
            + "and f.dueDate >= :startOfToday and f.dueDate < :startOfTomorrow")
    long countDueToday(@Param("companyId") UUID companyId, @Param("startOfToday") Instant startOfToday,
                        @Param("startOfTomorrow") Instant startOfTomorrow);

    @Query("select count(f) from FollowUp f where f.companyId = :companyId and f.status not in "
            + "(com.courier.modules.followup.domain.FollowUpStatus.COMPLETED, "
            + "com.courier.modules.followup.domain.FollowUpStatus.CANCELLED) and f.dueDate >= :startOfTomorrow")
    long countUpcoming(@Param("companyId") UUID companyId, @Param("startOfTomorrow") Instant startOfTomorrow);

    @Query("select count(f) from FollowUp f where f.companyId = :companyId and f.status not in "
            + "(com.courier.modules.followup.domain.FollowUpStatus.COMPLETED, "
            + "com.courier.modules.followup.domain.FollowUpStatus.CANCELLED) "
            + "and f.priority = com.courier.modules.followup.domain.FollowUpPriority.URGENT")
    long countUrgent(@Param("companyId") UUID companyId);

    /** Every active company's open follow-ups still to sweep for due-today/overdue
     *  notifications — {@code FollowUpSweepJob} runs cross-company on a scheduler thread. */
    @Query("select f from FollowUp f where f.status not in "
            + "(com.courier.modules.followup.domain.FollowUpStatus.COMPLETED, "
            + "com.courier.modules.followup.domain.FollowUpStatus.CANCELLED) "
            + "and f.assignedUserId is not null "
            + "and (f.overdueNotified = false or f.dueTodayNotified = false)")
    List<FollowUp> findAllOpenPendingSweep();
}
