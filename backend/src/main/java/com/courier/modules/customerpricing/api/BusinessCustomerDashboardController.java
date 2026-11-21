package com.courier.modules.customerpricing.api;

import com.courier.modules.customerpricing.application.BusinessCustomerDashboard;
import com.courier.modules.customerpricing.application.BusinessCustomerDashboardService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business-customers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "TBB Customer Dashboard", description = "Overview of TBB customers, zones, rates and bookings")
public class BusinessCustomerDashboardController {

    private final BusinessCustomerDashboardService service;

    @GetMapping("/dashboard")
    @Operation(summary = "TBB customer overview", description = "`COMPANY_ADMIN`. Counts, setup gaps and "
            + "per-customer booking activity. Bookings are matched on the consignor's mobile number and exclude "
            + "cancelled shipments.")
    public ApiResponse<BusinessCustomerDashboard> overview() {
        return ApiResponse.success(service.overview());
    }

    @GetMapping("/dashboard/revenue-trend")
    @Operation(summary = "Monthly revenue trend", description = "`COMPANY_ADMIN`. Net booking amount and booking count per "
            + "month for the last `months` months (1-24, default 6), for one TBB customer or all of them.")
    public ApiResponse<java.util.List<BusinessCustomerDashboardService.RevenuePoint>> revenueTrend(
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) java.util.UUID customerId) {
        return ApiResponse.success(service.revenueTrend(months, customerId));
    }
}
