package com.courier.modules.shipment.api;

import com.courier.modules.shipment.api.dto.PublicTrackResponse;
import com.courier.modules.shipment.application.PublicTrackingService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public parcel tracking — no bearer token required. Listed at {@code /api/v1/track/**} in
 * {@code SecurityConfig.PUBLIC_ENDPOINTS}; kept as its own controller/service pair (see
 * {@link PublicTrackingService}) rather than a new method on the authenticated {@code
 * ShipmentController}, so the "no auth, redacted response" boundary can't be blurred by a
 * missing {@code @PreAuthorize} on a shared class. Reached from the login screen's "Track
 * Shipment" link.
 */
@RestController
@RequestMapping("/api/v1/track")
@RequiredArgsConstructor
@Tag(name = "Public Tracking", description = "Track a shipment by AWB/tracking or shipment number — no login required")
public class PublicTrackController {

    private final PublicTrackingService publicTrackingService;

    @GetMapping("/{number}")
    @Operation(summary = "Track a shipment (public)",
            description = "Redacted projection — status, cities and timeline only, no "
                    + "address/contact/pricing detail. Matched against either the tracking "
                    + "number (AWB) or the shipment number.")
    public ApiResponse<PublicTrackResponse> track(@PathVariable String number) {
        return ApiResponse.success(publicTrackingService.track(number));
    }
}
