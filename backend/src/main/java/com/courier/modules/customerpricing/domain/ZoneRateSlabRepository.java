package com.courier.modules.customerpricing.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ZoneRateSlabRepository extends JpaRepository<ZoneRateSlab, UUID> {

    @Query("select s from ZoneRateSlab s where s.companyId = :companyId and s.rateId = :rateId order by s.minWeight")
    List<ZoneRateSlab> findByRate(@Param("companyId") UUID companyId, @Param("rateId") UUID rateId);
}
