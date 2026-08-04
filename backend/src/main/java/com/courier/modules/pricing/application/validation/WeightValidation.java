package com.courier.modules.pricing.application.validation;

import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@code actualWeight > 0}, and any supplied dimension is positive. Runs before
 * {@code domain.VolumetricCalculator}/{@code domain.ChargeableWeightCalculator}, which
 * assume both — a dimension of zero would silently zero out the volumetric-weight product
 * rather than the "no dimensions captured" case those calculators are built to tolerate.
 */
@Component
public class WeightValidation {

    public void validate(PricingCommand command) {
        if (command.actualWeight() == null || command.actualWeight().signum() <= 0) {
            throw new BusinessRuleException("Actual weight must be greater than zero.");
        }
        requirePositiveIfPresent(command.length(), "Length");
        requirePositiveIfPresent(command.width(), "Width");
        requirePositiveIfPresent(command.height(), "Height");
    }

    private void requirePositiveIfPresent(BigDecimal value, String label) {
        if (value != null && value.signum() <= 0) {
            throw new BusinessRuleException(label + " must be greater than zero.");
        }
    }
}
