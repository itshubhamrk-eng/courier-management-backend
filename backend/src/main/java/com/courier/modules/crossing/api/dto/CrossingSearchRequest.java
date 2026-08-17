package com.courier.modules.crossing.api.dto;

import com.courier.modules.crossing.domain.CrossingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Query parameters of {@code GET /api/v1/crossings}. */
@Schema(name = "CrossingSearchRequest", description = "Crossing search filters")
public record CrossingSearchRequest(UUID shipmentId, UUID branchId, CrossingStatus status) {
    public static CrossingSearchRequest empty() {
        return new CrossingSearchRequest(null, null, null);
    }
}
