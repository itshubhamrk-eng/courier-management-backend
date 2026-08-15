package com.courier.modules.distance.infrastructure;

import com.courier.modules.distance.application.routing.RoutingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OSRM adapter. One call: driving route between two points, distance + duration only
 * ({@code overview=false} — the turn-by-turn geometry is not needed here).
 */
@Slf4j
public class OsrmRoutingService implements RoutingPort {

    private final RestClient restClient;

    public OsrmRoutingService(RoutingProperties properties, RestClient.Builder builder) {
        var requestFactory = ClientHttpRequestFactoryBuilder.detect().build(
                ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(properties.getConnectTimeout())
                        .withReadTimeout(properties.getReadTimeout()));
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<RouteResult> route(Coordinates from, Coordinates to) {
        if (from == null || from.latitude() == null || from.longitude() == null
                || to == null || to.latitude() == null || to.longitude() == null) {
            return Optional.empty();
        }

        String coordinates = "%s,%s;%s,%s".formatted(
                from.longitude().toPlainString(), from.latitude().toPlainString(),
                to.longitude().toPlainString(), to.latitude().toPlainString());

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/route/v1/driving/" + coordinates)
                            .queryParam("overview", "false")
                            .queryParam("alternatives", "false")
                            .queryParam("steps", "false")
                            .build())
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("Routing lookup failed for {} -> {}", from, to, e);
            return Optional.empty();
        }

        if (response == null || !"Ok".equals(response.get("code"))) {
            log.warn("OSRM returned no route for {} -> {} (code {})",
                    from, to, response == null ? null : response.get("code"));
            return Optional.empty();
        }

        List<Map<String, Object>> routes = (List<Map<String, Object>>) response.get("routes");
        if (routes == null || routes.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> first = routes.get(0);
        Object distance = first.get("distance");
        Object duration = first.get("duration");
        if (distance == null || duration == null) {
            return Optional.empty();
        }

        return Optional.of(new RouteResult(
                new BigDecimal(distance.toString()), new BigDecimal(duration.toString())));
    }
}
