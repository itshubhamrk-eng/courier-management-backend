package com.courier.modules.districtfreight.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateDistrictLevelFreightCommand(
        UUID branchId,
        UUID districtId,
        UUID stateId,
        BigDecimal rate1To15,
        BigDecimal rate16To50,
        BigDecimal rate51To100,
        BigDecimal rate101To1000,
        BigDecimal rate1001To1500,
        BigDecimal rate1501To2000,
        Boolean odaApplicable,
        BigDecimal odaCharge
) {
    /** District-level shape (no state) — what every pre-state caller uses. */
    public CreateDistrictLevelFreightCommand(
            UUID branchId, UUID districtId, BigDecimal rate1To15, BigDecimal rate16To50, BigDecimal rate51To100,
            BigDecimal rate101To1000, BigDecimal rate1001To1500, BigDecimal rate1501To2000,
            Boolean odaApplicable, BigDecimal odaCharge) {
        this(branchId, districtId, null, rate1To15, rate16To50, rate51To100, rate101To1000, rate1001To1500,
                rate1501To2000, odaApplicable, odaCharge);
    }
}
