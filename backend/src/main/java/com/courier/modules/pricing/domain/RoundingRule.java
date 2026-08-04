package com.courier.modules.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How the final net amount is rounded, after every charge calculator has run. Configurable
 * per {@link PricingConfiguration#roundingRule()} — a company billing in a currency with no
 * subunit in practice (most Indian couriers round freight to the nearest rupee) picks
 * {@link #NEAREST_ONE}; a company that wants the raw two-decimal total picks {@link #NONE}.
 */
public enum RoundingRule {

    /** No rounding beyond the standard 2-decimal money scale. */
    NONE {
        @Override
        public BigDecimal apply(BigDecimal amount) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
    },
    /** Nearest whole unit, e.g. 128.30 -> 128, 128.50 -> 129 (HALF_UP). */
    NEAREST_ONE {
        @Override
        public BigDecimal apply(BigDecimal amount) {
            return amount.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        }
    },
    /** Nearest 5, e.g. 128.30 -> 130. */
    NEAREST_FIVE {
        @Override
        public BigDecimal apply(BigDecimal amount) {
            return nearestMultiple(amount, new BigDecimal(5));
        }
    },
    /** Nearest 10, e.g. 128.30 -> 130. */
    NEAREST_TEN {
        @Override
        public BigDecimal apply(BigDecimal amount) {
            return nearestMultiple(amount, new BigDecimal(10));
        }
    };

    public abstract BigDecimal apply(BigDecimal amount);

    private static BigDecimal nearestMultiple(BigDecimal amount, BigDecimal multiple) {
        BigDecimal units = amount.divide(multiple, 0, RoundingMode.HALF_UP);
        return units.multiply(multiple).setScale(2, RoundingMode.HALF_UP);
    }
}
