package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Query parameters of {@code GET /api/v1/shipments}, bound as a parameter object. */
@Schema(name = "ShipmentSearchRequest", description = "Shipment search filters")
public record ShipmentSearchRequest(
        Set<ShipmentStatus> status,
        UUID bookingBranchId,
        UUID deliveryBranchId,
        @Schema(description = "Where the shipment physically is right now — for a crossing "
                + "hub's own \"what can I manifest from here\" query, use this instead of "
                + "bookingBranchId")
        UUID currentLocationId,
        @Schema(description = "The shipment's next stop (a crossing hub, or the delivery "
                + "branch once every hop is done) — use this instead of deliveryBranchId "
                + "for the same reason")
        UUID nextLocationId,
        UUID manifestId,
        LocalDate bookingDateFrom,
        LocalDate bookingDateTo,
        @Schema(description = "Delivery Report filter — matched against when the shipment was actually delivered")
        LocalDate deliveredDateFrom,
        LocalDate deliveredDateTo,
        @Size(max = 100)
        @Schema(description = "Free text over shipment number and tracking number")
        String search
) {
    public static ShipmentSearchRequest empty() {
        return new ShipmentSearchRequest(null, null, null, null, null, null, null, null, null, null, null);
    }
}
