package com.courier.modules.manifest.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** Aggregates for GET /api/v1/manifests/summary — same filters as the list endpoint. */
@Schema(name = "ManifestSummaryStatsResponse", description = "Unpaged aggregates for a manifest search")
public record ManifestSummaryStatsResponse(
        long totalManifests,
        long totalShipments,
        BigDecimal totalWeight,
        long totalPackages
) {
}
