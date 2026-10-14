package com.courier.modules.pdp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PdpRateSlabRepository extends JpaRepository<PdpRateSlab, UUID> {

    @Query("select s from PdpRateSlab s where s.companyId = :companyId and s.rateId = :rateId order by s.minWeight")
    List<PdpRateSlab> findByRate(@Param("companyId") UUID companyId, @Param("rateId") UUID rateId);
}
