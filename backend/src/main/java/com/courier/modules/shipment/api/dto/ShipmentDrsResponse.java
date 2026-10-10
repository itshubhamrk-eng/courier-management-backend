package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "ShipmentDrsResponse",
        description = "The DRS (delivery run) this one shipment is on — read from its DeliveryAssignment row")
public record ShipmentDrsResponse(
        String drsNumber, UUID deliveryBranchId, UUID deliveryUserId, UUID vehicleId,
        BigDecimal fuelCost, BigDecimal deliveryCharge, String status,
        Instant assignedAt, Instant deliveredAt, String receiverName, String deliveryRemarks
) {
}
