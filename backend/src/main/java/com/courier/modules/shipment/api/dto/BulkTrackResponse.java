package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "BulkTrackResponse", description = "Bulk-track results, one row per submitted number")
public record BulkTrackResponse(List<BulkTrackRowResponse> results, long foundCount, long notFoundCount) {
}
