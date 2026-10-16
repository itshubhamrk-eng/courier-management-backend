package com.courier.modules.busdepot.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusDivisionRepository extends JpaRepository<BusDivision, UUID> {

    @Query("select v from BusDivision v where v.id = :id and v.companyId = :companyId")
    Optional<BusDivision> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select v from BusDivision v where v.companyId = :companyId"
            + " and (:status is null or v.status = :status)"
            + " and (:search is null or lower(v.divisionName) like :search or lower(v.divisionCode) like :search)"
            + " order by v.divisionName")
    List<BusDivision> search(@Param("companyId") UUID companyId, @Param("status") BusDepotStatus status,
                             @Param("search") String search);

    @Query("select count(v) > 0 from BusDivision v where v.companyId = :companyId"
            + " and lower(v.divisionCode) = lower(:code) and (:excludeId is null or v.id <> :excludeId)")
    boolean existsByCode(@Param("companyId") UUID companyId, @Param("code") String code, @Param("excludeId") UUID excludeId);

    @Query("select count(v) > 0 from BusDivision v where v.companyId = :companyId"
            + " and lower(v.divisionName) = lower(:name) and (:excludeId is null or v.id <> :excludeId)")
    boolean existsByName(@Param("companyId") UUID companyId, @Param("name") String name, @Param("excludeId") UUID excludeId);
}
