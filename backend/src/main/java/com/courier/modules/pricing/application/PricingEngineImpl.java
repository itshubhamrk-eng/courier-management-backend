package com.courier.modules.pricing.application;

import com.courier.modules.company.application.CompanySettingsService;
import com.courier.modules.freight.application.FreightCalculationResult;
import com.courier.modules.freight.application.FreightFactorService;
import com.courier.modules.freight.application.command.FreightCalculationCommand;
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
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.RouteRateUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final FreightFactorService freightFactorService;
    private final CompanySettingsService companySettingsService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public PricingResult calculate(PricingCommand command) {
        weightValidation.validate(command);

        PricingConfiguration configuration = properties.toConfiguration();
        BigDecimal actualWeight = WeightCalculator.normalise(command.actualWeight());
        BigDecimal volumetricWeight = VolumetricCalculator.calculate(
                command.length(), command.width(), command.height(),
                configuration.volumetricDivisor());
        BigDecimal chargeableWeight =
                ChargeableWeightCalculator.calculate(actualWeight, volumetricWeight);

        try {
            Route route = routeValidation.validate(command);
            LocalDate bookingDate = bookingValidation.validate(command);
            List<Rate> candidates = rateValidation.validate(route.getId(), command, bookingDate);

            PricingContext context = new PricingContext(command, configuration);
            context.matchedRoute(route);
            context.candidates(candidates);
            context.bookingDate(bookingDate);
            context.actualWeight(actualWeight);
            context.volumetricWeight(volumetricWeight);
            context.chargeableWeight(chargeableWeight);

            PricingStrategy strategy = pricingFactory.resolve(context);
            return strategy.price(context);
        } catch (RouteRateUnavailableException noRouteRate) {
            return priceByDistanceAndWeight(command, actualWeight, volumetricWeight, chargeableWeight);
        }
    }

    /**
     * Fallback when there's no route, an inactive route, no active rate, or no weight
     * slab for this lane at all: the company-level Freight Factor grid ({@code freight =
     * matchedFactor * weight}, resolved via the booking/delivery branch pair's road
     * distance) — every caller of this engine (Shipment Booking, the frontend's own live
     * pricing preview, any future Quotation/mobile consumer) gets it for free, not just
     * whichever one remembers to catch {@link RouteRateUnavailableException} itself. No
     * fuel/handling/ODA/insurance/discount/round-off — Freight Factor deliberately carries
     * none of those. GST is still statutory here too: applied on top of the freight sum at
     * the company's own {@code CompanySettings.gstPercentage}, since there is no matched
     * {@link Rate} to read a per-lane percentage off of. A gap in the Freight Factor grid
     * itself, or an unresolvable branch-pair distance, still fails the call with its own
     * exception — there is no third fallback tier.
     *
     * <p>{@code command.freightFactorOverride()} lets a caller raise the matched cell's own
     * factor before freight is computed — never lower it. A smaller override is refused
     * outright (a desk can charge more than the grid says, never less); a null override
     * simply prices at the matched factor, unchanged from before this existed.
     *
     * <p>A gap in the grid — no cell covers this lane's distance/weight — no longer fails
     * the call: it prices at freight zero (still GSTed, still net-zero on top) instead of
     * throwing, so a caller with its own way to price the lane (District Level Freight, a
     * manual Other Charges entry) is not blocked by this grid alone being incomplete. An
     * unresolvable branch-pair distance (a branch with no geocoded lat/long) gets the same
     * treatment — freight zero, not a thrown error — on direct request: this grid is a
     * fallback for booking, not the authoritative freight source (District Level Freight
     * is), so a stale geocode on either branch must never block a real booking. See
     * {@code FreightFactorServiceImpl.tryCalculate}.
     */
    private PricingResult priceByDistanceAndWeight(PricingCommand command, BigDecimal actualWeight,
                                                    BigDecimal volumetricWeight, BigDecimal chargeableWeight) {
        var matchOutcome = freightFactorService.tryCalculate(new FreightCalculationCommand(
                command.bookingBranchId(), command.deliveryBranchId(), chargeableWeight));
        BigDecimal matchedFactor = matchOutcome.map(r -> r.matchedFactor().getFactor()).orElse(null);
        BigDecimal effectiveFactor = matchedFactor;
        BigDecimal chargesSubtotal = matchOutcome.map(FreightCalculationResult::freight).orElse(BigDecimal.ZERO);
        if (command.freightFactorOverride() != null) {
            if (matchedFactor != null && command.freightFactorOverride().compareTo(matchedFactor) < 0) {
                throw new BusinessRuleException(
                        "Freight factor can only be increased — matched %s, requested %s."
                                .formatted(matchedFactor.toPlainString(),
                                        command.freightFactorOverride().toPlainString()));
            }
            effectiveFactor = command.freightFactorOverride();
            chargesSubtotal = effectiveFactor.multiply(chargeableWeight).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal gstPercentage = companySettingsService.get().getGstPercentage();
        BigDecimal gstAmount = chargesSubtotal.multiply(gstPercentage)
                .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        BigDecimal netAmount = chargesSubtotal.add(gstAmount);
        return new PricingResult(null, null, actualWeight, volumetricWeight, chargeableWeight,
                chargesSubtotal, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                gstAmount, BigDecimal.ZERO, BigDecimal.ZERO, netAmount, effectiveFactor);
    }
}
