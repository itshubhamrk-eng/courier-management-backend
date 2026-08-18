package com.courier.modules.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Company-wide operational overview for a {@code COMPANY_ADMIN} (or platform-scoped
 * caller with no own branch) — null for a branch-scoped caller, whose layout doesn't
 * show this section anyway. See {@code DashboardServiceImpl.companyOverview}.
 */
public record CompanyOverviewResponse(
        List<PipelineStageResponse> pipeline,
        long readyForManifest,
        long manifestsAwaitingDispatch,
        long pendingDelivery,
        long delayedShipments,
        BigDecimal totalWalletBalance,
        long lowBalanceBranches,
        List<TopRouteResponse> topRoutes,
        List<TopCustomerResponse> topCustomers
) {
}
