package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DrsShipmentRowResponse(
        UUID shipmentId, String shipmentNumber, String trackingNumber,
        String receiverName, String receiverContact, UUID paymentModeId,
        BigDecimal netAmount, ShipmentStatus status, Instant deliveredAt
) {
}
