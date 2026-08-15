package com.courier.modules.distance.application.routing;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The seam between "resolve the road distance between two points" and whichever routing
 * engine a deployment uses — same split {@link com.courier.modules.company.application.geocoding.GeocodingPort}
 * draws for address lookup, but the opposite failure stance: a caller here asked for a
 * distance <em>on purpose</em> (to store it, to price a shipment), so a lookup failure is
 * surfaced to that caller rather than swallowed — {@code AddressDistanceService} is what
 * turns an empty {@link Optional} into a 503, not this port itself.
 */
public interface RoutingPort {

    Optional<RouteResult> route(Coordinates from, Coordinates to);

    record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }

    record RouteResult(BigDecimal distanceMeters, BigDecimal durationSeconds) {
    }
}
