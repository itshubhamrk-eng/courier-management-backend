package com.courier.modules.manifest.domain;

import java.math.BigDecimal;

/**
 * Unpaged aggregates over a manifest search — the THC Report summary row. Same
 * "modest company row count, in-memory reduce" shape as {@code ShipmentSummaryStats}.
 */
public record ManifestSummaryStats(
        long totalManifests,
        long totalShipments,
        BigDecimal totalWeight,
        long totalPackages
) {
}
