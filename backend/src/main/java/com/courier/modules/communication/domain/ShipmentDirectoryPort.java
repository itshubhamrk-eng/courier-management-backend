package com.courier.modules.communication.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * The seam that keeps this module from depending on {@code shipment}'s own domain entity —
 * same cross-module shape {@code TicketDirectoryPort}/{@code BranchDirectoryPort} already
 * use elsewhere in this project (interface owned by the consumer, implemented by the data
 * owner: {@code shipment.infrastructure.CommunicationShipmentDirectoryAdapter}).
 *
 * <p>Read at dispatch time, not carried on the triggering event — a snapshot on the event
 * itself would be stale by the time a retry re-reads it minutes later (the same reasoning
 * {@code ShipmentEvent}'s own class doc gives for why its records carry only ids/scalars).
 */
public interface ShipmentDirectoryPort {

    Optional<ShipmentSnapshot> findSnapshot(UUID companyId, UUID shipmentId);
}
