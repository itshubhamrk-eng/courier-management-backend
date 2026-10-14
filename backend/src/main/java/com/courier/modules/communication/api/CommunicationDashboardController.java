package com.courier.modules.communication.api;

import com.courier.modules.communication.api.dto.CommunicationDashboardResponse;
import com.courier.modules.communication.application.CommunicationDashboardService;
import com.courier.modules.communication.application.WhatsAppRateSyncService;
import com.courier.modules.communication.api.dto.FetchedRatesResponse;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Total Sent/Delivered/Failed/Pending, plus today's per-channel breakdown. */
@RestController
@RequestMapping("/api/v1/communication/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Communication Dashboard", description = "Today's send/delivery/failure statistics")
public class CommunicationDashboardController {

    private final CommunicationDashboardService service;
    private final CommunicationMapper mapper;
    private final WhatsAppRateSyncService rateSync;

    @GetMapping
    @Operation(summary = "Communication statistics for a period", description = "period = TODAY (default), "
            + "YESTERDAY, LAST_7_DAYS or LAST_30_DAYS, cut at midnight India time. Delivered and Read need "
            + "the Meta webhook; lastReceiptAt null means it has never reached this deployment.")
    public ApiResponse<CommunicationDashboardResponse> summary(
            @RequestParam(defaultValue = "TODAY") CommunicationDashboardService.Period period) {
        return ApiResponse.success(mapper.toResponse(service.summary(period)));
    }

    @PostMapping("/fetch-rates")
    @Operation(summary = "Fetch the per-message rates from Meta", description = "Reads what Meta actually billed "
            + "this WhatsApp account over the last 90 days (pricing analytics), works out each category's price per "
            + "delivered message, and saves it in Channel Settings. Meta publishes no rate card, so a category "
            + "that has never been billed gets no rate. Company admins only.")
    public ApiResponse<FetchedRatesResponse> fetchRates() {
        var r = rateSync.fetchAndSave();
        return ApiResponse.success(new FetchedRatesResponse(r.currency(), r.days(),
                r.rates().stream().map(d -> new FetchedRatesResponse.Derived(d.category(), d.volume(), d.cost(), d.rate())).toList(),
                r.saved(), r.billedTotal(), r.note()), r.saved().isEmpty() ? "Nothing to fetch yet" : "Rates saved");
    }
}
