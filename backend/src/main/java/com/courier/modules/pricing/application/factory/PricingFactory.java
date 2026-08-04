package com.courier.modules.pricing.application.factory;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.strategy.PricingStrategy;

/**
 * Resolves the {@link PricingStrategy} that should price a given {@link PricingContext} —
 * the Factory the module's brief asks for, paired with the Strategy interface it selects
 * between. Today there is one registered strategy; the factory is what lets a future one
 * (promotional, surge) be added without {@code application.PricingEngineImpl} knowing it
 * exists.
 */
public interface PricingFactory {

    PricingStrategy resolve(PricingContext context);
}
