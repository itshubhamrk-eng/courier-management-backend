package com.courier.modules.communication.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** WhatsApp spend this month versus the monthly budget. Spend, not a balance — Meta exposes none. */
public record WhatsAppSpendResponse(String level, String currency, BigDecimal spent, BigDecimal budget,
                                    BigDecimal percentUsed, Instant periodStart, Instant periodEnd, String note) {
}
