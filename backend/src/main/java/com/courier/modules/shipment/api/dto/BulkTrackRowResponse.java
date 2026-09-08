package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of a bulk-track result — one per number the caller submitted, in the order
 * submitted (duplicates collapsed). {@code shipment} is null when {@code found} is false.
 */
@Schema(name = "BulkTrackRowResponse", description = "Bulk-track result row")
public record BulkTrackRowResponse(String number, boolean found, ShipmentSummaryResponse shipment) {
}
