package com.courier.modules.pricing.application;

import com.courier.modules.master.domain.Route;
import com.courier.modules.rate.domain.Rate;

import java.math.BigDecimal;

/**
 * The priced outcome of {@link PricingEngine#calculate}. Assembled by
 * {@code strategy.StandardPricingStrategy} from a spent {@link PricingContext}.
 * {@code api.PricingMapper} converts this to {@code api.dto.PricingResponse}.
 */
public record PricingResult(
        Route matchedRoute,
        Rate matchedRate,
        BigDecimal actualWeight,
        BigDecimal volumetricWeight,
        BigDecimal chargeableWeight,
        BigDecimal freight,
        BigDecimal fuelCharge,
        BigDecimal handlingCharge,
        BigDecimal odaCharge,
        BigDecimal insuranceCharge,
        BigDecimal gstAmount,
        BigDecimal discountAmount,
        BigDecimal roundOff,
        BigDecimal netAmount,
        /** The Freight Factor grid cell's own factor, or the caller's override of it if one
         *  was supplied and accepted — null except on the Freight Factor fallback path (see
         *  {@code PricingEngineImpl.priceByDistanceAndWeight}), since the normal Route/Rate
         *  path never uses a freight factor at all. */
        BigDecimal appliedFreightFactor
) {
}
