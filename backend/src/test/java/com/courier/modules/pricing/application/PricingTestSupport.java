package com.courier.modules.pricing.application;

import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.pricing.domain.PricingConfiguration;
import com.courier.modules.pricing.domain.RoundingRule;
import com.courier.modules.rate.domain.Rate;
import com.courier.modules.rate.domain.RateStatus;
import com.courier.modules.rate.domain.WeightUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Shared fixtures for the pricing calculator/strategy/engine tests. */
public final class PricingTestSupport {

    public static final UUID BOOKING_BRANCH = UUID.randomUUID();
    public static final UUID DELIVERY_BRANCH = UUID.randomUUID();
    public static final UUID SERVICE_TYPE = UUID.randomUUID();
    public static final UUID PACKAGE_TYPE = UUID.randomUUID();
    public static final UUID PAYMENT_MODE = UUID.randomUUID();

    private PricingTestSupport() {
    }

    public static Rate rate(String code, String min, String max) {
        Rate rate = Rate.builder()
                .rateCode(code)
                .rateName("Rate " + code)
                .routeId(UUID.randomUUID())
                .serviceTypeId(SERVICE_TYPE)
                .packageTypeId(PACKAGE_TYPE)
                .paymentModeId(PAYMENT_MODE)
                .minimumWeight(new BigDecimal(min))
                .maximumWeight(new BigDecimal(max))
                .weightUnit(WeightUnit.KG)
                .baseRate(new BigDecimal("100.00"))
                .additionalWeight(new BigDecimal("0.500"))
                .additionalWeightRate(new BigDecimal("20.00"))
                .minimumCharge(BigDecimal.ZERO)
                .fuelSurcharge(new BigDecimal("10.00"))
                .handlingCharge(new BigDecimal("5.00"))
                .odaCharge(new BigDecimal("15.00"))
                .insuranceCharge(new BigDecimal("8.00"))
                .gstPercentage(new BigDecimal("18"))
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .status(RateStatus.ACTIVE)
                .build();
        rate.setVersion(0L);
        return rate;
    }

    public static PricingCommand command(BigDecimal actualWeight) {
        return command(actualWeight, null, null, null, null, null, null);
    }

    public static PricingCommand command(BigDecimal actualWeight, BigDecimal length,
                                         BigDecimal width, BigDecimal height,
                                         BigDecimal declaredValue, BigDecimal discountPercentage,
                                         BigDecimal discountAmount) {
        return new PricingCommand(BOOKING_BRANCH, DELIVERY_BRANCH, "411001", "400001",
                SERVICE_TYPE, PACKAGE_TYPE, PAYMENT_MODE, actualWeight, length, width, height,
                declaredValue, LocalDate.of(2026, 6, 1), discountPercentage, discountAmount);
    }

    public static PricingConfiguration configuration() {
        return PricingConfiguration.defaults();
    }

    public static PricingConfiguration configuration(boolean fuel, boolean oda, boolean insurance,
                                                      boolean discount, RoundingRule rule) {
        return new PricingConfiguration(PricingConfiguration.DEFAULT_VOLUMETRIC_DIVISOR,
                fuel, oda, insurance, discount, rule);
    }

    /** A context with a matched rate and chargeable weight already resolved — what every
     * calculator except {@code FreightCalculator} expects to find. */
    public static PricingContext contextWithMatchedRate(Rate matchedRate, BigDecimal chargeableWeight,
                                                         PricingCommand command,
                                                         PricingConfiguration configuration) {
        PricingContext context = new PricingContext(command, configuration);
        context.candidates(List.of(matchedRate));
        context.matchedRate(matchedRate);
        context.chargeableWeight(chargeableWeight);
        return context;
    }

    public static PricingContext contextWithCandidates(List<Rate> candidates,
                                                        BigDecimal chargeableWeight,
                                                        PricingCommand command,
                                                        PricingConfiguration configuration) {
        PricingContext context = new PricingContext(command, configuration);
        context.candidates(candidates);
        context.chargeableWeight(chargeableWeight);
        return context;
    }
}
