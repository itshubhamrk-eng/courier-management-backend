package com.courier.modules.districtfreight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "FreightCalculationResponse", description = "District Level Freight's own breakdown for one booking")
public record FreightCalculationResponse(
        UUID matchedFreightId,
        UUID bookingBranchId, String bookingBranchCode, String bookingBranchName,
        UUID districtId, String districtCode, String districtName,
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
