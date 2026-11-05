package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.SessionRefreshEvent;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.scan.ScanRule;
import com.courier.modules.security.domain.IncidentRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One session refreshed from two or more distinct networks inside the window.
 *
 * <p>What it measures: refresh tokens issued in the window ({@code refresh_tokens} rows, one
 * per login/rotation, each stamped with the requesting IP) grouped by session; a session
 * with 2+ distinct IP prefixes (IPv4 /24, IPv6 /64) fires. Loopback, private, link-local,
 * carrier-grade-NAT and unparseable addresses are ignored. Access-token request activity is
 * not used: {@code activity_logs.session_id} is never populated. One request per user.
 *
 * <p>Sensitivity is LOW: a client refreshes only about every 15 minutes (access-token TTL)
 * while the scan window is 10 minutes, so a session usually has at most one token in any
 * window and 2+ prefixes needs a login plus a refresh, or two refreshes, close together.
 * It catches concurrent use of a stolen refresh token, not slow drift.
 */
@Component
@RequiredArgsConstructor
public class SessionMultiIpRule implements ScanRule {

    /** Evidence lists at most this many prefixes. */
    private static final int MAX_PREFIXES_IN_EVIDENCE = 5;

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public IncidentRule code() {
        return IncidentRule.SESSION_MULTI_IP;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        Map<UUID, Set<String>> prefixesBySession = new LinkedHashMap<>();
        Map<UUID, SessionRefreshEvent> ownerBySession = new LinkedHashMap<>();
        for (SessionRefreshEvent event : refreshTokenRepository.findSessionRefreshEvents(from, to)) {
            Optional<String> prefix = IpPrefix.of(event.ipAddress());
            if (prefix.isEmpty()) {
                continue;
            }
            prefixesBySession.computeIfAbsent(event.sessionId(), k -> new LinkedHashSet<>()).add(prefix.get());
            ownerBySession.putIfAbsent(event.sessionId(), event);
        }

        Map<UUID, IncidentRequest> byUser = new LinkedHashMap<>();
        prefixesBySession.forEach((sessionId, prefixes) -> {
            SessionRefreshEvent owner = ownerBySession.get(sessionId);
            if (prefixes.size() < 2 || byUser.containsKey(owner.userId())) {
                return;
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("sessionId", sessionId.toString());
            evidence.put("distinctPrefixCount", prefixes.size());
            evidence.put("prefixes", prefixes.stream().limit(MAX_PREFIXES_IN_EVIDENCE).toList());
            evidence.put("windowFrom", from.toString());
            evidence.put("windowTo", to.toString());
            byUser.put(owner.userId(), new IncidentRequest(
                    owner.companyId(), owner.userId(), IncidentRule.SESSION_MULTI_IP, evidence));
        });
        return List.copyOf(byUser.values());
    }
}
