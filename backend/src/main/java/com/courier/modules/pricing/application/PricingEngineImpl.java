package com.courier.modules.pricing.application;

import com.courier.modules.master.domain.Route;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.pricing.application.factory.PricingFactory;
import com.courier.modules.pricing.application.strategy.PricingStrategy;
import com.courier.modules.pricing.application.validation.BookingValidation;
import com.courier.modules.pricing.application.validation.RateValidation;
import com.courier.modules.pricing.application.validation.RouteValidation;
import com.courier.modules.pricing.application.validation.WeightValidation;
import com.courier.modules.pricing.domain.ChargeableWeightCalculator;
import com.courier.modules.pricing.domain.PricingConfiguration;
import com.courier.modules.pricing.domain.VolumetricCalculator;
import com.courier.modules.pricing.domain.WeightCalculator;
import com.courier.modules.rate.domain.Rate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Orchestrates the module's flow: Validate Route -> Validate Serviceability -> Validate
 * Rate -> Calculate Volumetric Weight -> Calculate Chargeable Weight -> Execute Charge
 * Calculators -> Return Charge Breakup. Every step is delegated: this class sequences,
 * it does not itself decide a route is inactive or compute a surcharge.
 *
 * <p>{@code isAuthenticated()} — the same read tier {@code RateServiceImpl.calculate} uses.
 * A caller within a request thread (the REST endpoint, or a future Shipment/Quotation
 * module calling this bean in-process) already carries the {@code SecurityContext} and
 * {@code CompanyContext} that {@link RouteValidation}, {@link RateValidation} and
 * {@link BookingValidation} need from the modules they call into — this class binds
 * neither itself, it is stateless.
 */
@Service
@RequiredArgsConstructor
public class PricingEngineImpl implements PricingEngine {

    private final RouteValidation routeValidation;
    private final BookingValidation bookingValidation;
    private final RateValidation rateValidation;
    private final WeightValidation weightValidation;
    private final PricingFactory pricingFactory;
    private final PricingProperties properties;

    @Override
    @PreAuthorize("isAuthenticated()")
    public PricingResult calculate(PricingCommand command) {
        weightValidation.validate(command);
        Route route = routeValidation.validate(command);
        LocalDate bookingDate = bookingValidation.validate(command);
        List<Rate> candidates = rateValidation.validate(route.getId(), command, bookingDate);

        PricingConfiguration configuration = properties.toConfiguration();
        PricingContext context = new PricingContext(command, configuration);
        context.matchedRoute(route);
        context.candidates(candidates);
        context.bookingDate(bookingDate);

        BigDecimal actualWeight = WeightCalculator.normalise(command.actualWeight());
        BigDecimal volumetricWeight = VolumetricCalculator.calculate(
                command.length(), command.width(), command.height(),
                configuration.volumetricDivisor());
        BigDecimal chargeableWeight =
                ChargeableWeightCalculator.calculate(actualWeight, volumetricWeight);

        context.actualWeight(actualWeight);
        context.volumetricWeight(volumetricWeight);
        context.chargeableWeight(chargeableWeight);

        PricingStrategy strategy = pricingFactory.resolve(context);
        return strategy.price(context);
    }
}
