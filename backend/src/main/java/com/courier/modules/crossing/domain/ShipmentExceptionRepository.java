package com.courier.modules.crossing.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentExceptionRepository extends JpaRepository<ShipmentException, UUID>,
        JpaSpecificationExecutor<ShipmentException> {

    @Query("select e from ShipmentException e where e.id = :id and e.companyId = :companyId")
    Optional<ShipmentException> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    long countByCompanyIdAndHubBranchIdAndStatus(UUID companyId, UUID hubBranchId, ShipmentExceptionStatus status);
}
