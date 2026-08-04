package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * A shipment's item grid. Not a {@code JpaSpecificationExecutor} — always read whole for
 * one shipment, the same treatment {@code CustomerAddressRepository} gives an address book.
 */
public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, UUID> {

    @Query("select i from ShipmentItem i where i.shipmentId = :shipmentId and i.companyId = :companyId "
            + "order by i.createdAt asc")
    List<ShipmentItem> findAllByShipmentIdWithinCompany(@Param("shipmentId") UUID shipmentId,
                                                        @Param("companyId") UUID companyId);

    /**
     * A hard delete, deliberately — unlike every soft-deleted aggregate in this project.
     * An item is a value of the shipment's own packing detail with no external reference
     * and no audit significance of its own; a full-replacement update on a still-{@code
     * BOOKED} shipment clears the grid and reinserts it rather than accumulating
     * soft-deleted rows on every edit.
     */
    void deleteAllByShipmentIdAndCompanyId(UUID shipmentId, UUID companyId);
}
