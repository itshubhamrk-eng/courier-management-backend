package com.courier.modules.busdepot.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bus depots, within a company. Single-row loads go through {@link #findByIdWithinCompany}. */
public interface BusDepotRepository extends JpaRepository<BusDepot, UUID> {

    @Query("select d from BusDepot d where d.id = :id and d.companyId = :companyId")
    Optional<BusDepot> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select d from BusDepot d where d.companyId = :companyId"
            + " and (:status is null or d.status = :status)"
            + " and (:type is null or d.depotType = :type)"
            + " and (:branchId is null or d.branchId = :branchId)"
            + " and (:divisionId is null or d.divisionId = :divisionId)"
            + " and (:search is null or lower(d.depotName) like :search or lower(d.depotCode) like :search"
            + "      or lower(d.city) like :search)"
            + " order by d.depotName")
    List<BusDepot> search(@Param("companyId") UUID companyId, @Param("status") BusDepotStatus status,
                          @Param("type") BusDepotType type, @Param("branchId") UUID branchId,
                          @Param("divisionId") UUID divisionId, @Param("search") String search);

    @Query("select count(d) > 0 from BusDepot d where d.companyId = :companyId and d.branchId = :branchId"
            + " and (:excludeId is null or d.id <> :excludeId)")
    boolean existsOtherForBranch(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
                                 @Param("excludeId") UUID excludeId);

    @Query("select count(d) > 0 from BusDepot d where d.companyId = :companyId and d.divisionId = :divisionId")
    boolean existsByDivision(@Param("companyId") UUID companyId, @Param("divisionId") UUID divisionId);

    @Query("select count(d) > 0 from BusDepot d where d.companyId = :companyId and d.parentDepotId = :depotId")
    boolean existsByParent(@Param("companyId") UUID companyId, @Param("depotId") UUID depotId);

    @Query("select count(d) > 0 from BusDepot d where d.companyId = :companyId"
            + " and lower(d.depotCode) = lower(:code) and (:excludeId is null or d.id <> :excludeId)")
    boolean existsByCode(@Param("companyId") UUID companyId, @Param("code") String code,
                         @Param("excludeId") UUID excludeId);

    @Query("select count(d) > 0 from BusDepot d where d.companyId = :companyId"
            + " and lower(d.depotName) = lower(:name) and (:excludeId is null or d.id <> :excludeId)")
    boolean existsByName(@Param("companyId") UUID companyId, @Param("name") String name,
                         @Param("excludeId") UUID excludeId);
}
