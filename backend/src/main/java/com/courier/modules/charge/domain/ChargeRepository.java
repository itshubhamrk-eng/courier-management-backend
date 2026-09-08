package com.courier.modules.charge.domain;

import com.courier.shared.domain.TimeOrderedUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Charges, within a company.
 *
 * <p>Company-owned: every derived query relies on {@code CompanyContext} being bound.
 * Single-row loads go through {@link #findByIdWithinCompany} — a primary-key load
 * bypasses the Hibernate filter. The name-uniqueness check is native and counts
 * soft-deleted rows, the same reasoning {@code RateRepository.isCodeTaken} documents.
 */
public interface ChargeRepository extends JpaRepository<Charge, UUID>, JpaSpecificationExecutor<Charge> {

    @Query("select c from Charge c where c.id = :id and c.companyId = :companyId")
    Optional<Charge> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    default boolean isNameTaken(UUID companyId, UUID serviceTypeId, String chargeName, UUID excludeId) {
        return countByNameIncludingDeleted(TimeOrderedUuid.toBytes(companyId),
                TimeOrderedUuid.toBytes(serviceTypeId), chargeName,
                excludeId == null ? null : TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    @Query(value = """
            SELECT COUNT(*) FROM charges
            WHERE company_id = :companyId AND service_type_id = :serviceTypeId
              AND LOWER(charge_name) = LOWER(:chargeName)
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByNameIncludingDeleted(@Param("companyId") byte[] companyId,
                                     @Param("serviceTypeId") byte[] serviceTypeId,
                                     @Param("chargeName") String chargeName,
                                     @Param("excludeId") byte[] excludeId);
}
