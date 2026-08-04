package com.courier.modules.pricing.application.strategy;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.PricingResult;
import com.courier.modules.pricing.application.calculator.ChargeCalculator;
import com.courier.modules.pricing.domain.ChargeType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * The default (and, today, only) pricing strategy: run every enabled
 * {@link ChargeCalculator} in {@link ChargeCalculator#order()} order, each reading whatever
 * earlier ones already wrote to {@link PricingContext}, then assemble a
 * {@link PricingResult} from the finished context.
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)} — the fallback
 * {@code com.courier.modules.pricing.application.factory.PricingFactory} reaches last, so a
 * future, more specific strategy can register with a higher precedence and be tried first
 * without this class changing.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class StandardPricingStrategy implements PricingStrategy {

    private final List<ChargeCalculator> calculators;

    @Override
    public boolean supports(PricingContext context) {
        return true;
    }

    @Override
    public PricingResult price(PricingContext context) {
        calculators.stream()
                .sorted(Comparator.comparingInt(ChargeCalculator::order))
                .forEach(calculator -> {
                    BigDecimal amount = calculator.isEnabled(context)
                            ? calculator.calculate(context)
                            : BigDecimal.ZERO.setScale(2);
                    context.charge(calculator.type(), amount);
                });

        return new PricingResult(
                context.matchedRoute(),
                context.matchedRate(),
                context.actualWeight(),
                context.volumetricWeight(),
                context.chargeableWeight(),
                context.charge(ChargeType.FREIGHT),
                context.charge(ChargeType.FUEL),
                context.charge(ChargeType.HANDLING),
                context.charge(ChargeType.ODA),
                context.charge(ChargeType.INSURANCE),
                context.charge(ChargeType.GST),
                context.charge(ChargeType.DISCOUNT),
                context.charge(ChargeType.ROUND_OFF),
                context.netAmount());
    }
}
