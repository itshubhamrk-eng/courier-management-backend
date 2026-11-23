package com.courier.modules.communication.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What Meta has billed this month against the budget the admin set. Meta exposes no prepaid
 * balance, so this is spend versus the company's own budget — never a "remaining balance".
 */
public interface WhatsAppSpendService {

    Spend current();

    /**
     * @param level       NOT_CONFIGURED, NO_BUDGET, OK (&lt;80%), WARN (&gt;=80%), OVER (&gt;=100%)
     * @param spent       null when Meta could not be read (see {@code note})
     * @param budget      null when no usable monthly budget is set
     * @param percentUsed spent / budget * 100, two decimals; null without both
     */
    record Spend(String level, String currency, BigDecimal spent, BigDecimal budget, BigDecimal percentUsed,
                 Instant periodStart, Instant periodEnd, String note) {
    }
}
