package com.courier.modules.pricing.application.calculator;

import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.pricing.domain.ChargeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Applies a discount against {@link PricingContext#totalBeforeDiscount()} (Freight + Fuel +
 * Handling + ODA + Insurance + GST), when discounts are enabled and the request asked for
 * one. {@code discountPercentage} takes precedence over {@code discountAmount} when a
 * caller supplies both; the result is clamped to {@code [0, totalBeforeDiscount]} so a
 * discount can never turn a quote negative.
 */
@Component
public class DiscountCalculator implements ChargeCalculator {

    @Override
    public ChargeType type() {
        return ChargeType.DISCOUNT;
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public boolean isEnabled(PricingContext context) {
        if (!context.configuration().discountEnabled()) {
            return false;
        }
        PricingCommand command = context.command();
        return isPositive(command.discountPercentage()) || isPositive(command.discountAmount());
    }

    @Override
    public BigDecimal calculate(PricingContext context) {
        PricingCommand command = context.command();
        BigDecimal total = context.totalBeforeDiscount();

        BigDecimal discount;
        if (isPositive(command.discountPercentage())) {
            discount = total.multiply(command.discountPercentage())
                    .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = command.discountAmount().setScale(2, RoundingMode.HALF_UP);
        }

        if (discount.compareTo(total) > 0) {
            discount = total;
        }
        return discount.max(BigDecimal.ZERO);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
