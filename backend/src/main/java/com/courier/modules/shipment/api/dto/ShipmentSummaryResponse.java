package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * List-row projection of a shipment.
 *
 * <p>{@code netAmount} is not a column on {@code shipments} — it lives on the separate
 * {@code shipment_charges} row this module writes at booking time (see {@code
 * ShipmentCharge}) — so the service batch-fetches it per page rather than per row, and
 * it comes in {@code null} only for a row that somehow has no charge record.
 */
@Schema(name = "ShipmentSummaryResponse", description = "Shipment list row")
public record ShipmentSummaryResponse(
        UUID id, String shipmentNumber, String trackingNumber, LocalDate bookingDate,
        UUID bookingBranchId, UUID deliveryBranchId, UUID currentLocationId, UUID nextLocationId,
        UUID manifestId, UUID paymentModeId,
        String senderName, String senderContact, String receiverName, String receiverContact,
        BigDecimal chargeableWeight, BigDecimal netAmount,
        BigDecimal totalCommission, BigDecimal commissionOnBasicFreight,
        BigDecimal branchCommissionOnOtherAmount, BigDecimal companyCommissionOnBasicFreight,
        ShipmentStatus status, Instant deliveredAt, Instant createdDate, Long version
) {
}
