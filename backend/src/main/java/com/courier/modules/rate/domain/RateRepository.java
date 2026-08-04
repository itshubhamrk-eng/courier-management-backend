package com.courier.modules.rate.domain;

import com.courier.shared.domain.TimeOrderedUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rates, within a company.
 *
 * <p>Company-owned: every derived query relies on {@code CompanyContext} being bound.
 * Single-row loads go through {@link #findByIdWithinCompany} — a primary-key load
 * bypasses the Hibernate filter. The code uniqueness check is native and counts
 * soft-deleted rows, the same reasoning {@code CustomerRepository} documents.
 */
public interface RateRepository extends JpaRepository<Rate, UUID>, JpaSpecificationExecutor<Rate> {

    @Query("select r from Rate r where r.id = :id and r.companyId = :companyId")
    Optional<Rate> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    /**
     * Every ACTIVE rate for one Route + Service Type + Package Type + Payment Mode
     * combination — the candidate set for both the overlap check and the calculator's
     * slab lookup.
     */
    List<Rate> findByCompanyIdAndRouteIdAndServiceTypeIdAndPackageTypeIdAndPaymentModeIdAndStatus(
            UUID companyId, UUID routeId, UUID serviceTypeId, UUID packageTypeId,
            UUID paymentModeId, RateStatus status);

    default boolean isCodeTaken(UUID companyId, String rateCode, UUID excludeId) {
        return countByCodeIncludingDeleted(TimeOrderedUuid.toBytes(companyId), rateCode,
                excludeId == null ? null : TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    @Query(value = """
            SELECT COUNT(*) FROM rate_master
            WHERE company_id = :companyId AND rate_code = :rateCode
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByCodeIncludingDeleted(@Param("companyId") byte[] companyId,
                                     @Param("rateCode") String rateCode,
                                     @Param("excludeId") byte[] excludeId);
}
