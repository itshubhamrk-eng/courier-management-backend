package com.courier.modules.customerpricing.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneRateRepository extends JpaRepository<ZoneRate, UUID> {

    @Query("select r from ZoneRate r where r.id = :id and r.companyId = :companyId")
    Optional<ZoneRate> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select r from ZoneRate r where r.companyId = :companyId and r.zoneId = :zoneId")
    Optional<ZoneRate> findByZone(@Param("companyId") UUID companyId, @Param("zoneId") UUID zoneId);

    @Query("select r from ZoneRate r where r.companyId = :companyId and r.zoneId in :zoneIds")
    List<ZoneRate> findByZones(@Param("companyId") UUID companyId, @Param("zoneIds") Collection<UUID> zoneIds);
}
