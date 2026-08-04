package com.courier.modules.dashboard.api;

import com.courier.modules.dashboard.api.dto.DashboardSummaryResponse;
import com.courier.modules.dashboard.application.DashboardService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The operational dashboard's single summary endpoint. Any authenticated user may call
 * it (see {@code SecurityConfig}'s default {@code anyRequest().authenticated()}) — the
 * figures are scoped to the caller's company by the standard Hibernate filter, the same
 * as every other company-owned read.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/summary")
    @Operation(summary = "Operational dashboard summary",
            description = "Shipment-derived counts, revenue and recent shipments for the "
                    + "caller's company. Branch/hub/company counts are not included here — "
                    + "the frontend already derives those from the live list endpoints.")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.success(service.summary());
    }
}
