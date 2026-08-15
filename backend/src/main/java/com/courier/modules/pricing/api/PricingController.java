package com.courier.modules.pricing.api;

import com.courier.modules.pricing.api.dto.PricingRequest;
import com.courier.modules.pricing.api.dto.PricingResponse;
import com.courier.modules.pricing.application.PricingEngine;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP face of the reusable {@link PricingEngine} — one endpoint, no persistence. Any
 * authenticated company user may call it (the same read tier Rate Master's calculator
 * uses): a booking clerk quoting a shipment needs this as much as an admin editing rate
 * cards does.
 */
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Pricing Engine", description = "Stateless shipment charge calculation")
public class PricingController {

    private final PricingEngine pricingEngine;
    private final PricingMapper mapper;

    @PostMapping("/calculate")
    @Operation(summary = "Calculate a shipment's charges", description = "Validates the "
            + "route, serviceability, and rate; computes volumetric and chargeable weight; "
            + "runs every enabled charge calculator; returns the full breakup. Prices "
            + "without booking — no data is persisted.")
    public ApiResponse<PricingResponse> calculate(@Valid @RequestBody PricingRequest request) {
        var command = mapper.toCommand(request);
        var result = pricingEngine.calculate(command);
        return ApiResponse.success(mapper.toResponse(command, result), "Price calculated");
    }
}
