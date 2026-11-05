package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.SessionRefreshEvent;
import com.courier.modules.security.application.GeoCountryService;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.application.scan.ScanRule;
import com.courier.modules.security.domain.GeoVerdict;
import com.courier.modules.security.domain.IncidentRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A session's tokens being used from outside the allowed country (auto-ban).
 *
 * <p>What it measures: refresh tokens issued in the window ({@code refresh_tokens} rows,
 * one per login and per rotation, written by {@code TokenIssuer}) with their
 * {@code ip_address}, which comes from {@code ClientIpResolver} (nginx's X-Real-IP), not a
 * client-controlled header. Each distinct IP is geo-checked once; a user with at least one
 * FOREIGN verdict raises one request. ALLOWED and UNKNOWN (private, unparseable, not in the
 * database) raise nothing, and when geo checking is disabled the rule reads nothing.
 *
 * <p>Since a foreign login is refused at the door, a foreign row here is in practice a
 * refresh from abroad: a refresh token minted at home and replayed elsewhere.
 *
 * <p>Deliberately NOT read: {@code activity_logs.ip_address}. It is the first
 * X-Forwarded-For entry, which the client can set to anything, so it must not drive a ban.
 * Access-token requests between refreshes are therefore not observed; detection lags by up
 * to one access-token TTL (the next refresh).
 */
@Component
@RequiredArgsConstructor
public class ForeignTokenUseRule implements ScanRule {

    /** Evidence lists at most this many IPs and sessions. */
    private static final int MAX_IN_EVIDENCE = 5;

    private final RefreshTokenRepository refreshTokenRepository;
    private final GeoCountryService geoCountryService;
    private final SecurityProperties properties;

    @Override
    public IncidentRule code() {
        return IncidentRule.FOREIGN_TOKEN_USE;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        if (!properties.getGeo().isEnabled()) {
            return List.of();
        }
        Map<String, Optional<String>> countryByIp = new HashMap<>();
        Map<UUID, Hit> byUser = new LinkedHashMap<>();
        for (SessionRefreshEvent event : refreshTokenRepository.findSessionRefreshEvents(from, to)) {
            String ip = event.ipAddress();
            if (ip == null || event.userId() == null) {
                continue;
            }
            Optional<String> country = countryByIp.computeIfAbsent(ip, geoCountryService::countryOf);
            if (geoCountryService.verdictFor(country) != GeoVerdict.FOREIGN) {
                continue;
            }
            Hit hit = byUser.computeIfAbsent(event.userId(), k -> new Hit(event.companyId()));
            hit.ips.add(ip);
            country.ifPresent(hit.countries::add);
            if (event.sessionId() != null) {
                hit.sessions.add(event.sessionId().toString());
            }
        }

        return byUser.entrySet().stream().map(e -> {
            Hit hit = e.getValue();
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("foreignIpCount", hit.ips.size());
            evidence.put("foreignIps", hit.ips.stream().limit(MAX_IN_EVIDENCE).toList());
            evidence.put("countries", hit.countries.stream().limit(MAX_IN_EVIDENCE).toList());
            evidence.put("sessionIds", hit.sessions.stream().limit(MAX_IN_EVIDENCE).toList());
            evidence.put("windowFrom", from.toString());
            evidence.put("windowTo", to.toString());
            return new IncidentRequest(hit.companyId, e.getKey(), IncidentRule.FOREIGN_TOKEN_USE, evidence);
        }).toList();
    }

    private static final class Hit {
        final UUID companyId;
        final Set<String> ips = new LinkedHashSet<>();
        final Set<String> countries = new LinkedHashSet<>();
        final Set<String> sessions = new LinkedHashSet<>();

        Hit(UUID companyId) {
            this.companyId = companyId;
        }
    }
}
