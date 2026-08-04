package com.courier.modules.rate.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param bookingDate defaults to today when null — the date the rate's
 *                     {@code effectiveFrom}/{@code effectiveTo} window is checked against
 */
public record RateCalculationCommand(
        UUID bookingBranchId,
        UUID deliveryBranchId,
        UUID serviceTypeId,
        UUID packageTypeId,
        UUID paymentModeId,
        BigDecimal actualWeight,
        LocalDate bookingDate
) {
}
