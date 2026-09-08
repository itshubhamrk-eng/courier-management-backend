package com.courier.modules.dashboard.api.dto;

import java.util.UUID;

/**
 * One row of the branch dashboard's "Rejected POD" backlog — a shipment whose latest POD
 * verification is {@code FAIL}, still needing a re-upload. See {@code
 * DashboardServiceImpl.branchOverview}.
 */
public record RejectedPodResponse(
        UUID shipmentId,
        String shipmentNumber,
        String receiverName,
        String reason
) {
}
