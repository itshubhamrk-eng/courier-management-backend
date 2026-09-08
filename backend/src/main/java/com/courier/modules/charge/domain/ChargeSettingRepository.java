package com.courier.modules.charge.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Charge Settings, within a company. Company-owned: every derived query relies on
 * {@code CompanyContext} being bound. Single-row loads go through
 * {@link #findByIdWithinCompany} — a primary-key load bypasses the Hibernate filter.
 */
public interface ChargeSettingRepository extends JpaRepository<ChargeSetting, UUID> {

    @Query("select s from ChargeSetting s where s.id = :id and s.companyId = :companyId")
    Optional<ChargeSetting> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    /** Every setting under one charge, in any status — "show associated charge settings". */
    List<ChargeSetting> findByCompanyIdAndChargeIdOrderByCreatedAtAsc(UUID companyId, UUID chargeId);

    /** The candidate set for the overlap check: every ACTIVE setting under one charge. */
    List<ChargeSetting> findByCompanyIdAndChargeIdAndStatus(UUID companyId, UUID chargeId, ChargeStatus status);

    /** Delete-guard for a Charge: does it still have any live (non-deleted) setting? */
    long countByCompanyIdAndChargeId(UUID companyId, UUID chargeId);
}
