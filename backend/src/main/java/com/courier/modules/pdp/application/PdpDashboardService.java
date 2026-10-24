package com.courier.modules.pdp.application;

import com.courier.modules.pdp.api.PdpDashboardDtos.DashboardResponse;

import java.util.UUID;

/** Read-only figures for the Pickup-Delivery Partner dashboard. */
public interface PdpDashboardService {

    /**
     * @param branchId one partner to narrow to (company admin); ignored for a partner-bound caller, who always sees
     *                 only their own branch. Null = every partner, with a per-partner breakdown.
     */
    DashboardResponse dashboard(UUID branchId);
}
