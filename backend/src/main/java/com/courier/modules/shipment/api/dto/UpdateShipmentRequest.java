package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/shipments/{id}}. Full replacement, only while the shipment is
 * still {@code BOOKED}. {@code bookingBranchId} is absent — immutable once booked.
 */
@Schema(name = "UpdateShipmentRequest", description = "Full replacement of a still-BOOKED shipment")
public record UpdateShipmentRequest(
        @NotNull Long version,
        @NotNull UUID deliveryBranchId,
        @NotBlank @Size(max = 10) String pickupPincode,
        @NotBlank @Size(max = 10) String deliveryPincode,
        @NotBlank @Size(max = 150) String senderName,
        @NotBlank @Size(max = 500) String senderAddress,
        @NotBlank @Size(max = 20) String senderContact,
        @NotBlank @Size(max = 150) String receiverName,
        @NotBlank @Size(max = 500) String receiverAddress,
        @NotBlank @Size(max = 20) String receiverContact,
        @NotNull UUID serviceTypeId,
        @NotNull UUID packageTypeId,
        @NotNull UUID paymentModeId,
        ShipmentType shipmentType,
        LocalDate bookingDate,
        @DecimalMin(value = "0") BigDecimal declaredValue,
        @Min(1) Integer numberOfPackages,
        @Size(max = 500) String remarks,
        @Valid List<ShipmentItemRequest> items,
        BigDecimal actualWeight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height
) {
}
