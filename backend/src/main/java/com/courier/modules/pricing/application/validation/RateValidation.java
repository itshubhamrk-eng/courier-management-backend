package com.courier.modules.pricing.application.validation;

import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.rate.application.RateService;
import com.courier.modules.rate.domain.Rate;
import com.courier.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Flow step 3: confirm a rate card exists for this combination — <i>not</i> weight-slab
 * matching, which needs the chargeable weight this runs ahead of. Reuses
 * {@link RateService#findActiveCandidates}, the seam added to Rate Master for this module,
 * mirroring how {@code RateServiceImpl.calculate} itself gathers candidates.
 *
 * <p>An empty result here is "Rate Not Found"; a non-empty result that still matches no
 * slab once the weight is known is a different failure ("Weight Slab Not Found"),
 * surfaced later by {@code calculator.FreightCalculator}.
 */
@Component
@RequiredArgsConstructor
public class RateValidation {

    private final RateService rateService;

    public List<Rate> validate(UUID routeId, PricingCommand command, LocalDate bookingDate) {
        List<Rate> candidates = rateService.findActiveCandidates(routeId, command.serviceTypeId(),
                command.packageTypeId(), command.paymentModeId(), bookingDate);
        if (candidates.isEmpty()) {
            throw new BusinessRuleException(
                    ("No active rate is effective on %s for this route, service type, "
                            + "package type and payment mode.").formatted(bookingDate));
        }
        return candidates;
    }
}
