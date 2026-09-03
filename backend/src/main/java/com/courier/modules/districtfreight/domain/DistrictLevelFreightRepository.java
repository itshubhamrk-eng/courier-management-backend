package com.courier.modules.districtfreight.domain;

import com.courier.shared.domain.TimeOrderedUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * District Level Freight rows, within a company. Same shape as {@code RateRepository} —
 * company-owned, single-row loads through {@link #findByIdWithinCompany} (a primary-key
 * load bypasses the Hibernate filter), duplicate-combination check is native so it also
 * sees soft-deleted rows (the unique key does not reference {@code deleted}).
 */
public interface DistrictLevelFreightRepository
        extends JpaRepository<DistrictLevelFreight, UUID>, JpaSpecificationExecutor<DistrictLevelFreight> {

    @Query("select f from DistrictLevelFreight f where f.id = :id and f.companyId = :companyId")
    Optional<DistrictLevelFreight> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    List<DistrictLevelFreight> findByCompanyIdAndBranchIdAndStatus(
            UUID companyId, UUID branchId, DistrictFreightStatus status);

    /** The Excel import's upsert lookup: does this From Station + District combination
     *  already have a (non-deleted) row to update, or does one need creating? */
    Optional<DistrictLevelFreight> findByCompanyIdAndBranchIdAndDistrictId(
            UUID companyId, UUID branchId, UUID districtId);

    default boolean isComboTaken(UUID companyId, UUID branchId, UUID districtId, UUID excludeId) {
        return countByComboIncludingDeleted(TimeOrderedUuid.toBytes(companyId),
                TimeOrderedUuid.toBytes(branchId), TimeOrderedUuid.toBytes(districtId),
                excludeId == null ? null : TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    @Query(value = """
            SELECT COUNT(*) FROM district_level_freight
            WHERE company_id = :companyId AND branch_id = :branchId AND district_id = :districtId
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByComboIncludingDeleted(@Param("companyId") byte[] companyId,
                                       @Param("branchId") byte[] branchId,
                                       @Param("districtId") byte[] districtId,
                                       @Param("excludeId") byte[] excludeId);
}
