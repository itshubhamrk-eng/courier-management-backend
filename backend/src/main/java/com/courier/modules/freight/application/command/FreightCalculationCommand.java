package com.courier.modules.freight.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param fromBranchId resolved to a distance via {@code AddressDistanceService
 *                      .resolveBranchDistance}
 * @param toBranchId   see {@link #fromBranchId}
 * @param weight       plain kg, matched against the grid's weight range
 */
public record FreightCalculationCommand(
        UUID fromBranchId,
        UUID toBranchId,
        BigDecimal weight
) {
}
