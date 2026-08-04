package com.courier.modules.dashboard.api.dto;

import java.math.BigDecimal;

/**
 * The shipment-derived figures the summary endpoint actually knows how to compute.
 *
 * <p>Deliberately not every field the frontend's {@code DashboardStatistics} model
 * declares — {@code activeBranches}/{@code totalCompanies}/{@code activeCompanies} are
 * already derived client-side from the live {@code /branches} and {@code /companies}
 * list endpoints, and hub figures have no module behind them yet. Omitted here, they
 * degrade to zero/null on the client rather than being duplicated or fabricated.
 * {@code walletBalance} is the caller's own branch wallet's spendable
 * ({@code availableBalance}, not {@code totalBalance} — a hold isn't spendable), null for
 * a caller with no own branch (company/platform admins, whose layout doesn't show the tile
 * anyway).
 */
public record DashboardStatisticsResponse(
        long todayShipments,
        long delivered,
        long inTransit,
        long pending,
        BigDecimal totalRevenue,
        long todayBookings,
        BigDecimal todayCollection,
        long pendingDelivery,
        long totalShipments,
        BigDecimal walletBalance
) {
}
