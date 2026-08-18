package com.courier.modules.dashboard.api.dto;

import java.util.List;

/**
 * Trailing daily trend data for the dashboard's three charts. Every series is real
 * per-day aggregation over {@code Shipment}/{@code ShipmentCharge} — days with no
 * activity are zero-filled by {@code DashboardServiceImpl}, not simply omitted, so the
 * chart's x-axis stays a continuous date range. {@code deliveryPerformance} is empty
 * (not null) for a genuinely cross-tenant {@code SUPER_ADMIN} read — no profile shows
 * that chart at platform scope.
 */
public record DashboardChartsResponse(
        List<ChartSeriesResponse> shipmentTrend,
        List<ChartSeriesResponse> deliveryPerformance,
        List<ChartSeriesResponse> revenueTrend
) {
}
