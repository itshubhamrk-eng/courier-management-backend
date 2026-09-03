package com.courier.modules.districtfreight.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The authoritative outcome of {@link FreightCalculationService#calculate} — every field
 * Shipment Booking's own "Freight Calculation" section displays, plus what {@code
 * ShipmentServiceImpl} needs to feed the freight amount into the existing commission
 * calculation unchanged.
 */
public record FreightCalculationResult(
        UUID matchedFreightId,
        UUID bookingBranchId,
        String bookingBranchCode,
        String bookingBranchName,
        UUID districtId,
        String districtCode,
        String districtName,
        String destinationPincode,
        BigDecimal chargeableWeight,
        String weightSlabLabel,
        BigDecimal ratePerKg,
        BigDecimal baseFreight,
        boolean odaApplicable,
        BigDecimal odaCharge,
        BigDecimal totalFreight
) {
}
