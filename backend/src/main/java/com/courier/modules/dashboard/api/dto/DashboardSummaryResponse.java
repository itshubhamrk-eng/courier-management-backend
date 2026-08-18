package com.courier.modules.dashboard.api.dto;

import java.util.List;

/** The payload behind {@code GET /api/v1/dashboard/summary}. */
public record DashboardSummaryResponse(
        DashboardStatisticsResponse statistics,
        List<RecentShipmentResponse> recentShipments,
        List<DashboardActivityResponse> recentActivity,
        CompanyOverviewResponse companyOverview,
        BranchOverviewResponse branchOverview,
        DashboardChartsResponse charts
) {
}
