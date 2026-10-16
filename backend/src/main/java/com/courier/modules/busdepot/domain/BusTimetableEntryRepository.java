package com.courier.modules.busdepot.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BusTimetableEntryRepository extends JpaRepository<BusTimetableEntry, UUID> {

    @Query("select e from BusTimetableEntry e where e.companyId = :companyId and e.depotId = :depotId"
            + " order by e.sortOrder")
    List<BusTimetableEntry> findBoard(@Param("companyId") UUID companyId, @Param("depotId") UUID depotId);

    /** Every board row of the company — the service groups them to show which depots have a board. */
    @Query("select e from BusTimetableEntry e where e.companyId = :companyId")
    List<BusTimetableEntry> findAllInCompany(@Param("companyId") UUID companyId);
}
