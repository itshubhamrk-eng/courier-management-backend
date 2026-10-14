package com.courier.modules.communication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "FetchedRatesResponse", description = "Rates worked out from what Meta has actually billed")
public record FetchedRatesResponse(
        @Schema(description = "Meta's billing currency for the account; null if it did not say") String currency,
        int days,
        List<Derived> rates,
        @Schema(description = "Categories whose rate was written to Channel Settings") List<String> saved,
        BigDecimal billedTotal,
        @Schema(description = "One plain sentence on what happened, including 'nothing billed yet'") String note
) {
    @Schema(name = "FetchedRate")
    public record Derived(String category, long volume, BigDecimal cost, BigDecimal rate) {
    }
}
