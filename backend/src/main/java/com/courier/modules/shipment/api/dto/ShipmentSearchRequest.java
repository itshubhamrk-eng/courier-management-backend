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
        return new ShipmentSearchRequest(null, null, null, null, null, null, null, null, null);
    }
}
