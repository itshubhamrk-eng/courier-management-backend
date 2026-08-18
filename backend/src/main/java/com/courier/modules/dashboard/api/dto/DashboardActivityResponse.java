package com.courier.modules.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One row on the dashboard's Recent Activity timeline. {@code kind} is one of
 *  {@code BOOKING}/{@code DELIVERY}/{@code WALLET} — matches the frontend's
 *  {@code ActivityKind} union exactly. */
public record DashboardActivityResponse(
        String id,
        String kind,
        String title,
        String detail,
        Instant at,
        BigDecimal amount
) {
}
