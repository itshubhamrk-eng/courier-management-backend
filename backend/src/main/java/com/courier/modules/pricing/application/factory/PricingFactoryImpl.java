package com.courier.modules.pricing.application.factory;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.strategy.PricingStrategy;
import com.courier.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tries every registered {@link PricingStrategy} in Spring {@code @Order} — a future,
 * more specific strategy first, {@code strategy.StandardPricingStrategy} (registered at
 * {@code LOWEST_PRECEDENCE}) last — and resolves to the first that
 * {@link PricingStrategy#supports(PricingContext)}.
 */
@Component
@RequiredArgsConstructor
public class PricingFactoryImpl implements PricingFactory {

    private final List<PricingStrategy> strategies;

    @Override
    public PricingStrategy resolve(PricingContext context) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(context))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "No pricing strategy is registered for this request."));
    }
}
