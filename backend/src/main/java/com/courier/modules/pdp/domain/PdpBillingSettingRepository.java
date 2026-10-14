package com.courier.modules.pdp.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PdpBillingSettingRepository extends JpaRepository<PdpBillingSetting, UUID> {

    @Query("select s from PdpBillingSetting s where s.companyId = :companyId and s.branchId = :branchId")
    Optional<PdpBillingSetting> findByBranch(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId);

    @Query("select s from PdpBillingSetting s where s.companyId = :companyId")
    List<PdpBillingSetting> findAllInCompany(@Param("companyId") UUID companyId);
}
