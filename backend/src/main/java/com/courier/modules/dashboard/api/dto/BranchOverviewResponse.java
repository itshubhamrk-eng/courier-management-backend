package com.courier.modules.dashboard.api.dto;

import java.util.List;

/**
 * Branch-scoped equivalent of {@code CompanyOverviewResponse} for a caller with an own
 * branch (BRANCH_MANAGER/BRANCH_OPERATOR, or a hub-scoped role) — the pipeline and
 * action-required backlog for their own branch specifically, not the whole company.
 * No wallet total / top routes / top customers here: those are already covered by the
 * caller's own Wallet KPI tile and are company-wide concepts, not branch ones. Null for
 * a caller with no own branch, whose layout shows {@code companyOverview} instead. See
 * {@code DashboardServiceImpl.branchOverview}.
 */
public record BranchOverviewResponse(
        List<PipelineStageResponse> pipeline,
        long readyForManifest,
        long manifestsAwaitingDispatch,
        long pendingDelivery,
        long delayedShipments
) {
}
