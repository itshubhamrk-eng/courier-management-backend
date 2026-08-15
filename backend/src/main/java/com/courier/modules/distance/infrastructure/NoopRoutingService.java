package com.courier.modules.distance.infrastructure;

import com.courier.modules.distance.application.routing.RoutingPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/** Used when {@code app.routing.enabled=false}. Every lookup is a miss. */
@Slf4j
public class NoopRoutingService implements RoutingPort {

    @Override
    public Optional<RouteResult> route(Coordinates from, Coordinates to) {
        return Optional.empty();
    }
}
