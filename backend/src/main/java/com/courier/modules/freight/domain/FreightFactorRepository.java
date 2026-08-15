package com.courier.modules.freight.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Freight factor grid cells, within a company.
 *
 * <p>Company-owned: every derived query relies on {@code CompanyContext} being bound.
 * Single-row loads go through {@link #findByIdWithinCompany} — a primary-key load
 * bypasses the Hibernate filter, the same reasoning every other repository in the
 * codebase documents.
 */
public interface FreightFactorRepository
        extends JpaRepository<FreightFactor, UUID>, JpaSpecificationExecutor<FreightFactor> {

    @Query("select f from FreightFactor f where f.id = :id and f.companyId = :companyId")
    Optional<FreightFactor> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    /** Every ACTIVE cell in the company — the candidate set for both the overlap check
     * and the calculate lookup. */
    List<FreightFactor> findByCompanyIdAndStatus(UUID companyId, FreightFactorStatus status);
}
