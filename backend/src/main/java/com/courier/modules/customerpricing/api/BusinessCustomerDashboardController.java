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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business-customers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Business Customer Dashboard", description = "Overview of business customers, zones, rates and bookings")
public class BusinessCustomerDashboardController {

    private final BusinessCustomerDashboardService service;

    @GetMapping("/dashboard")
    @Operation(summary = "Business customer overview", description = "`COMPANY_ADMIN`. Counts, setup gaps and "
            + "per-customer booking activity. Bookings are matched on the consignor's mobile number and exclude "
            + "cancelled shipments.")
    public ApiResponse<BusinessCustomerDashboard> overview() {
        return ApiResponse.success(service.overview());
    }
}
