package com.courier.modules.dashboard.application;

import com.courier.modules.dashboard.api.dto.DashboardSummaryResponse;

public interface DashboardService {

    /**
     * Operational figures for the caller's company (scoped by {@code CompanyContext}
     * the same way every other company-owned query is), or across every company when
     * called with no company bound — a platform-level caller.
     */
    DashboardSummaryResponse summary();
}
