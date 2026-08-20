package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.modules.shipment.domain.ShipmentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Full representation of a shipment, including its item grid. Nulls are serialised. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ShipmentResponse", description = "Shipment in full, with its item grid")
public record ShipmentResponse(
        UUID id, UUID companyId,
        String shipmentNumber, String trackingNumber,
        LocalDate bookingDate,
        UUID bookingBranchId, UUID deliveryBranchId, UUID manifestId,
        UUID currentLocationId, UUID nextLocationId,
        String pickupPincode, String deliveryPincode,
        String senderName, String senderAddress, String senderContact,
        String receiverName, String receiverAddress, String receiverContact,
        UUID serviceTypeId, UUID packageTypeId, UUID paymentModeId,
        ShipmentType shipmentType,
        LocalDate expectedDeliveryDate,
        BigDecimal actualWeight, BigDecimal volumetricWeight, BigDecimal chargeableWeight,
        BigDecimal declaredValue, Integer numberOfPackages,
        ShipmentStatus status,
        String remarks,
        Instant deliveredAt, String podPhotoUrl, String podSignatureUrl, String shipmentImageUrl,
        BigDecimal invoiceValue, boolean ewayBillRequired, EwayBillInfo ewayBill,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version,
        List<ShipmentItemResponse> items
) {
    /** The shipment's current E-Way Bill, read-only — plain fields only (no enum type),
     *  so this module never depends on {@code com.courier.modules.ewaybill}'s domain
     *  package, only its application interface (see {@code ShipmentMapper}). */
    @Schema(name = "ShipmentEwayBillInfo")
    public record EwayBillInfo(UUID id, String ewayBillNumber, String status, BigDecimal invoiceValue,
                               Instant validFrom, Instant validUntil, String documentUrl) {
    }
}
