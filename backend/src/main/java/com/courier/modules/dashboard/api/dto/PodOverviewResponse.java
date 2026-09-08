package com.courier.modules.dashboard.api.dto;

/**
 * POD Dashboard pie: every {@code OUT_FOR_DELIVERY}/{@code DELIVERED} shipment, bucketed by
 * its own POD state — a shipment with no verification row at all is {@code pendingUpload},
 * one whose latest verification is {@code REVIEW}/{@code PASS}/{@code FAIL} is {@code
 * pendingVerification}/{@code approved}/{@code rejected} respectively. See {@code
 * DashboardServiceImpl.podOverview}.
 */
public record PodOverviewResponse(
        long pendingUpload,
        long pendingVerification,
        long approved,
        long rejected
) {
}
