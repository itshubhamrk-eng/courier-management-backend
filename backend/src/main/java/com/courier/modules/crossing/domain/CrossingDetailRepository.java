package com.courier.modules.crossing.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrossingDetailRepository extends JpaRepository<CrossingDetail, UUID>,
        JpaSpecificationExecutor<CrossingDetail> {

    @Query("select c from CrossingDetail c where c.id = :id and c.companyId = :companyId")
    Optional<CrossingDetail> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    /** A shipment's whole crossing route, hop 0 first. */
    @Query("select c from CrossingDetail c where c.shipmentId = :shipmentId and c.companyId = :companyId "
            + "order by c.sequenceOrder asc")
    List<CrossingDetail> findByShipmentWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                      @Param("companyId") UUID companyId);
}
