package com.courier.modules.dashboard.api.dto;

/** One stage of the shipment lifecycle pipeline, with its month-to-date count. */
public record PipelineStageResponse(String stage, long count) {
}
