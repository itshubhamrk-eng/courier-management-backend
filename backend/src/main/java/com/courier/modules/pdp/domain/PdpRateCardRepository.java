package com.courier.modules.pdp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PdpRateCardRepository extends JpaRepository<PdpRateCard, UUID> {

    @Query("select r from PdpRateCard r where r.id = :id and r.companyId = :companyId")
    Optional<PdpRateCard> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("""
            select r from PdpRateCard r
            where r.companyId = :companyId
              and (:branchId is null or r.branchId = :branchId)
              and (:rateType is null or r.rateType = :rateType)
            order by r.branchId, r.rateType, r.effectiveFrom desc""")
    List<PdpRateCard> search(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
                             @Param("rateType") PdpRateType rateType);

    /** Every card of a partner and type, whatever its district — the caller filters by district and date. */
    @Query("select r from PdpRateCard r where r.companyId = :companyId and r.branchId = :branchId "
            + "and r.rateType = :rateType")
    List<PdpRateCard> findByBranchAndType(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
                                          @Param("rateType") PdpRateType rateType);
}
