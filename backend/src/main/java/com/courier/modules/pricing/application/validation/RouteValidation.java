package com.courier.modules.pricing.application.validation;

import com.courier.modules.master.application.RouteService;
import com.courier.modules.master.domain.Route;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.shared.exception.RouteRateUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Flow step 1: resolve the lane. Reuses
 * {@link RouteService#findByBranches(java.util.UUID, java.util.UUID)}, the seam Rate
 * Master added for this exact shape of caller — handed a booking and a delivery branch,
 * not a route id.
 *
 * @throws RouteRateUnavailableException no route runs between the two branches in this
 *         direction ("Route Not Found"), or the route exists but is inactive ("Inactive
 *         Route") — the signal Shipment Booking uses to fall back to Freight Factor
 *         pricing instead of failing the booking
 */
@Component
@RequiredArgsConstructor
public class RouteValidation {

    private final RouteService routeService;

    public Route validate(PricingCommand command) {
        Route route = routeService.findByBranches(command.bookingBranchId(), command.deliveryBranchId());
        if (!route.isActive()) {
            throw new RouteRateUnavailableException(
                    "Route %s is inactive and cannot be priced.".formatted(route.getCode()));
        }
        return route;
    }
}
