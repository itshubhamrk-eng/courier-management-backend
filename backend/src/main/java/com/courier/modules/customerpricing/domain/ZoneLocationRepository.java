package com.courier.modules.customerpricing.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneLocationRepository extends JpaRepository<ZoneLocation, UUID> {

    @Query("select l from ZoneLocation l where l.id = :id and l.companyId = :companyId")
    Optional<ZoneLocation> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select l from ZoneLocation l where l.companyId = :companyId and l.zoneId = :zoneId")
    List<ZoneLocation> findByZone(@Param("companyId") UUID companyId, @Param("zoneId") UUID zoneId);

    @Query("select l from ZoneLocation l where l.companyId = :companyId and l.zoneId in :zoneIds")
    List<ZoneLocation> findByZones(@Param("companyId") UUID companyId, @Param("zoneIds") Collection<UUID> zoneIds);

    /** Every live location of one customer in a district — candidates for the overlap check and the zone lookup. */
    @Query("select l from ZoneLocation l where l.companyId = :companyId and l.customerId = :customerId"
            + " and l.districtId = :districtId")
    List<ZoneLocation> findByCustomerAndDistrict(@Param("companyId") UUID companyId,
                                                 @Param("customerId") UUID customerId,
                                                 @Param("districtId") UUID districtId);
}
