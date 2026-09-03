package com.courier.modules.districtfreight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/district-level-freight}. {@code COMPANY_ADMIN} only. A new
 * row always starts ACTIVE — status has its own lifecycle endpoints.
 */
@Schema(name = "CreateDistrictLevelFreightRequest",
        description = "New District Level Freight rate row: From Station + District + six weight-slab rates + ODA")
public record CreateDistrictLevelFreightRequest(

        @NotNull @Schema(description = "Branch id — the From Station") UUID branchId,
        @NotNull @Schema(description = "District id — the destination district") UUID districtId,

        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(description = "Per-KG rate, 1 to 15 KG") BigDecimal rate1To15,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(description = "Per-KG rate, 16 to 50 KG") BigDecimal rate16To50,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(description = "Per-KG rate, 51 to 100 KG") BigDecimal rate51To100,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(description = "Per-KG rate, 101 to 1000 KG") BigDecimal rate101To1000,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(description = "Per-KG rate, 1001 to 1500 KG") BigDecimal rate1001To1500,
        @NotNull @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(description = "Per-KG rate, 1501 to 2000 KG") BigDecimal rate1501To2000,

        @Schema(description = "Whether ODA applies for this From Station + District. Defaults true.")
        Boolean odaApplicable,
        @DecimalMin(value = "0.0", message = "cannot be negative")
        @Schema(description = "ODA charge. Configurable, not hardcoded; defaults 250.00 when omitted.")
        BigDecimal odaCharge
) {
}
