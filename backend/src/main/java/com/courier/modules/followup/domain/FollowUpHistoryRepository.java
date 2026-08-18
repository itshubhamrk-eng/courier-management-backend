package com.courier.modules.followup.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FollowUpHistoryRepository extends JpaRepository<FollowUpHistory, UUID> {

    @Query("select h from FollowUpHistory h where h.companyId = :companyId and h.followUpId = :followUpId "
            + "order by h.createdAt asc")
    List<FollowUpHistory> findByFollowUp(@Param("followUpId") UUID followUpId, @Param("companyId") UUID companyId);
}
