package com.courier.modules.busdepot.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusCrossingRouteRepository extends JpaRepository<BusCrossingRoute, UUID> {

    @Query("select r from BusCrossingRoute r where r.id = :id and r.companyId = :companyId")
    Optional<BusCrossingRoute> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select r from BusCrossingRoute r where r.companyId = :companyId"
            + " and (:status is null or r.status = :status)"
            + " and (:depotId is null or r.fromDepotId = :depotId or r.toDepotId = :depotId)"
            + " order by r.updatedAt desc")
    List<BusCrossingRoute> search(@Param("companyId") UUID companyId, @Param("status") BusDepotStatus status,
                                  @Param("depotId") UUID depotId);

    @Query("select r from BusCrossingRoute r where r.companyId = :companyId"
            + " and r.fromDepotId in :froms and r.toDepotId in :tos and r.status = 'ACTIVE'"
            + " order by r.updatedAt desc")
    List<BusCrossingRoute> findActiveBetween(@Param("companyId") UUID companyId,
                                             @Param("froms") Collection<UUID> froms,
                                             @Param("tos") Collection<UUID> tos);

    @Query("select r from BusCrossingRoute r where r.companyId = :companyId"
            + " and r.fromDepotId = :from and r.toDepotId = :to")
    List<BusCrossingRoute> findByPair(@Param("companyId") UUID companyId, @Param("from") UUID from,
                                      @Param("to") UUID to);
}
