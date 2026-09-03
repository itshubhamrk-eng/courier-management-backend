package com.courier.modules.districtfreight.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code branchId}/{@code districtId} are included — an edit may re-point the row to a
 *  different station/district, still guarded by the same duplicate-combination check
 *  create uses. {@code expectedVersion} guards a 409. */
public record UpdateDistrictLevelFreightCommand(
        UUID branchId,
        UUID districtId,
        BigDecimal rate1To15,
        BigDecimal rate16To50,
        BigDecimal rate51To100,
        BigDecimal rate101To1000,
        BigDecimal rate1001To1500,
        BigDecimal rate1501To2000,
        Boolean odaApplicable,
        BigDecimal odaCharge,
        Long expectedVersion
) {
}
