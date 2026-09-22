package com.courier.modules.crossing.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface HubOutScanRepository extends JpaRepository<HubOutScan, UUID> {

    @Query("select h.shipmentId from HubOutScan h "
            + "where h.companyId = :companyId and h.manifestId = :manifestId and h.shipmentId in :shipmentIds")
    List<UUID> findScannedShipmentIds(@Param("companyId") UUID companyId, @Param("manifestId") UUID manifestId,
                                       @Param("shipmentIds") List<UUID> shipmentIds);

    boolean existsByCompanyIdAndManifestIdAndShipmentId(UUID companyId, UUID manifestId, UUID shipmentId);
}
