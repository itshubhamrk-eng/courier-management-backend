package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/** The Pricing Engine's own charge breakup, persisted at booking time. See {@code GET /api/v1/shipments/{id}/charges}. */
@Schema(name = "ShipmentChargeResponse", description = "Freight through Net Amount, plus the matched lane/rate")
public record ShipmentChargeResponse(
        UUID shipmentId,
        BigDecimal freight, BigDecimal fuelCharge, BigDecimal handlingCharge, BigDecimal odaCharge,
        BigDecimal insuranceCharge,
        @Schema(description = "Sum of ACTIVE charge-module rows (e.g. \"Hamali\") matched "
                + "to this shipment's service type and weight/distance. GST-inclusive, "
                + "unlike appointmentDeliveryCharge.")
        BigDecimal applicableCharges,
        BigDecimal gstAmount, BigDecimal discountAmount,
        BigDecimal roundOff, BigDecimal otherCharges,
        @Schema(description = "Manual, typed at booking time when appointmentDelivery is "
                + "checked. Deliberately GST-free — added straight into netAmount, never "
                + "folded into gstAmount.")
        BigDecimal appointmentDeliveryCharge,
        BigDecimal commissionOnBasicFreight, BigDecimal branchCommissionOnOtherAmount,
        BigDecimal companyCommissionOnBasicFreight, BigDecimal totalCommission, BigDecimal netAmount,
        UUID matchedRouteId, String matchedRouteCode,
        UUID matchedRateId, String matchedRateCode,
        @Schema(description = "The Freight Factor grid cell's own factor, or an accepted "
                + "override of it — null unless this shipment priced through the Freight "
                + "Factor fallback (matchedRouteId/matchedRateId both null).")
        BigDecimal appliedFreightFactor
) {
}
