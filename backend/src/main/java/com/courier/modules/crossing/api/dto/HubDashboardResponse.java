package com.courier.modules.crossing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "HubDashboardResponse", description = "Hub Operations dashboard figures for one hub")
public record HubDashboardResponse(
        long todaysInbound,
        long pendingInScan,
        long shipmentsAtHub,
        long pendingSorting,
        long readyForDispatch,
        long dispatchedToday,
        long pendingExceptions,
        long todaysLoadSheets
) {
}
