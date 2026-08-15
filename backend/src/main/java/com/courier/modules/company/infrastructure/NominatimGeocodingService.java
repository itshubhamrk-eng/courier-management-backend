package com.courier.modules.company.infrastructure;

import com.courier.modules.company.application.geocoding.GeocodingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nominatim (OpenStreetMap) adapter — free, keyless structured search. One call: the most
 * specific address fragments available, first hit wins.
 *
 * <p>Nominatim's usage policy caps the public instance at ~1 request/second and requires an
 * identifying {@code User-Agent} on every call (there is no key to authenticate with
 * instead) — see {@link GeocodingProperties#getUserAgent()}. Branch creation is low-volume
 * enough that no client-side throttle is added here; a deployment with heavier traffic
 * should point {@code app.geocoding.base-url} at a self-hosted or paid instance rather than
 * push the public one past its policy.
 */
@Slf4j
public class NominatimGeocodingService implements GeocodingPort {

    private final RestClient restClient;

    public NominatimGeocodingService(GeocodingProperties properties, RestClient.Builder builder) {
        var requestFactory = ClientHttpRequestFactoryBuilder.detect().build(
                ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(properties.getConnectTimeout())
                        .withReadTimeout(properties.getReadTimeout()));
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .build();
    }

    @Override
    public Optional<Coordinates> geocode(Query query) {
        if (isBlank(query.postalCode()) && isBlank(query.city()) && isBlank(query.area())
                && isBlank(query.district()) && isBlank(query.state())) {
            return Optional.empty();
        }

        // `district` in this app is user-typed and often a locality/taluka name (e.g.
        // "Kothrud"), not the formal administrative district Nominatim's `county` expects
        // for structured search — including it there over-constrains the query and
        // silently returns zero results even though postalcode+city alone would match.
        // Try with it first (free precision when the two line up), fall back without it.
        Optional<Coordinates> withDistrict = search(query, true);
        if (withDistrict.isPresent()) {
            return withDistrict;
        }
        if (!isBlank(query.district())) {
            Optional<Coordinates> withoutDistrict = search(query, false);
            if (withoutDistrict.isPresent()) {
                return withoutDistrict;
            }
        }
        log.info("Geocoding found no match for postal code {} / city {} / state {}",
                query.postalCode(), query.city(), query.state());
        return Optional.empty();
    }

    private Optional<Coordinates> search(Query query, boolean includeDistrict) {
        List<Map<String, Object>> results;
        try {
            results = restClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/search")
                                .queryParam("format", "json")
                                .queryParam("limit", 1);
                        if (!isBlank(query.postalCode())) {
                            b.queryParam("postalcode", query.postalCode());
                        }
                        if (!isBlank(query.city())) {
                            b.queryParam("city", query.city());
                        } else if (!isBlank(query.area())) {
                            b.queryParam("city", query.area());
                        }
                        if (includeDistrict && !isBlank(query.district())) {
                            b.queryParam("county", query.district());
                        }
                        if (!isBlank(query.state())) {
                            b.queryParam("state", query.state());
                        }
                        if (!isBlank(query.country())) {
                            b.queryParam("country", query.country());
                        }
                        return b.build();
                    })
                    .retrieve()
                    .body(List.class);
        } catch (Exception e) {
            // Best-effort: a lookup failure must never block creating the branch itself.
            log.warn("Geocoding lookup failed for postal code {} / city {}",
                    query.postalCode(), query.city(), e);
            return Optional.empty();
        }

        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> first = results.get(0);
        Object lat = first.get("lat");
        Object lon = first.get("lon");
        if (lat == null || lon == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Coordinates(new BigDecimal(lat.toString()), new BigDecimal(lon.toString())));
        } catch (NumberFormatException e) {
            log.warn("Geocoding returned an unusable coordinate pair: {} / {}", lat, lon);
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
