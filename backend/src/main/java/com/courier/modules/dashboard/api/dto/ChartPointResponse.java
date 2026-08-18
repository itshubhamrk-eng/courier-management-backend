package com.courier.modules.dashboard.api.dto;

import java.math.BigDecimal;

/** One (label, value) sample on a dashboard trend chart — a day and its figure. */
public record ChartPointResponse(String label, BigDecimal value) {
}
