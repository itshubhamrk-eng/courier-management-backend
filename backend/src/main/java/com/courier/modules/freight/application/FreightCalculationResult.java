package com.courier.modules.freight.application;

import com.courier.modules.freight.domain.FreightFactor;

import java.math.BigDecimal;

/**
 * The priced outcome of {@link FreightFactorService#calculate}.
 *
 * @param matchedFactor the grid cell the quote was priced against
 * @param distanceKm    resolved via {@code AddressDistanceService.resolveBranchDistance}
 * @param weight        as given in the request
 * @param freight       {@code matchedFactor.factor * weight}, scaled to money
 */
public record FreightCalculationResult(
        FreightFactor matchedFactor,
        BigDecimal distanceKm,
        BigDecimal weight,
        BigDecimal freight
) {
}
