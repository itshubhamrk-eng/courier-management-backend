package com.courier.modules.crossing.api.dto;

import com.courier.modules.crossing.domain.ShipmentExceptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Query parameters of {@code GET /api/v1/hub-operations/exceptions}. */
@Schema(name = "ExceptionSearchRequest", description = "Hub Operations exception search filters")
public record ExceptionSearchRequest(UUID shipmentId, UUID hubBranchId, ShipmentExceptionStatus status) {
    public static ExceptionSearchRequest empty() {
        return new ExceptionSearchRequest(null, null, null);
    }
}
