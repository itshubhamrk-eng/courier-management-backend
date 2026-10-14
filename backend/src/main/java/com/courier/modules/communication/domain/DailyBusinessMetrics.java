package com.courier.modules.communication.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The figures in the morning summary, all for one business day ({@code day}) except the two
 * {@code ...Now} counts, which are a live snapshot at the time the report is built — they have
 * no meaningful "as of yesterday" without a stored history of every status.
 *
 * @param bookings         shipments booked that day, cancelled ones excluded
 * @param revenue          net amount of those bookings
 * @param rechargeAmount   total wallet recharges credited that day
 * @param rechargeCount    how many recharges made up {@code rechargeAmount}
 * @param delivered        shipments that reached DELIVERED that day (by status-change time)
 * @param cancelled        shipments that were cancelled that day (by status-change time)
 * @param codToPaySettled  wallet entries of reason COD or TPY that day, either direction
 * @param inTransitNow     DISPATCHED or IN_SCAN right now
 * @param outForDeliveryNow OUT_FOR_DELIVERY right now
 */
public record DailyBusinessMetrics(
        String companyName,
        LocalDate day,
        long bookings,
        BigDecimal revenue,
        BigDecimal rechargeAmount,
        long rechargeCount,
        long delivered,
        long cancelled,
        BigDecimal codToPaySettled,
        long inTransitNow,
        long outForDeliveryNow
) {
}
