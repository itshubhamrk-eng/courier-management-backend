package com.courier.modules.company.infrastructure;

import com.courier.modules.company.application.geocoding.GeocodingPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/** Used when {@code app.geocoding.enabled=false}. Every lookup is a miss. */
@Slf4j
public class NoopGeocodingService implements GeocodingPort {

    @Override
    public Optional<Coordinates> geocode(Query query) {
        return Optional.empty();
    }
}
