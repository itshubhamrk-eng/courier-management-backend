package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.SessionRefreshEvent;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserSessionRepository;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.scan.ScanRule;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.useragent.UserAgentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A session that keeps getting refreshed by a different browser or OS than the one it was
 * created (logged in) with — a stolen refresh token being used from another client.
 *
 * <p>What it measures: refresh tokens issued in the window ({@code refresh_tokens} rows, one
 * per login/rotation, carrying the requesting user agent and IP) whose parsed browser/OS
 * differs from the {@code user_sessions} browser/OS recorded at login. Access-token request
 * activity is not used: {@code activity_logs.session_id} is never populated and the access
 * token carries no session id. Unknown/blank values on either side are never a change. One
 * request per user.
 */
@Component
@RequiredArgsConstructor
public class SessionAgentChangeRule implements ScanRule {

    private static final int MAX_UA_LENGTH = 255;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;

    @Override
    public IncidentRule code() {
        return IncidentRule.SESSION_AGENT_CHANGE;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        List<SessionRefreshEvent> events = refreshTokenRepository.findSessionRefreshEvents(from, to);
        if (events.isEmpty()) {
            return List.of();
        }
        Set<UUID> sessionIds = events.stream().map(SessionRefreshEvent::sessionId).collect(Collectors.toSet());
        Map<UUID, UserSession> sessions = userSessionRepository.findAllById(sessionIds).stream()
                .collect(Collectors.toMap(UserSession::getId, Function.identity(), (a, b) -> a));

        // One request per user: the first mismatching event is evidence enough.
        Map<UUID, IncidentRequest> byUser = new LinkedHashMap<>();
        for (SessionRefreshEvent event : events) {
            UserSession login = sessions.get(event.sessionId());
            if (login == null || byUser.containsKey(event.userId())
                    || !event.userId().equals(login.getUserId())) {
                continue;
            }
            UserAgentParser.Parsed seen = UserAgentParser.parse(event.userAgent());
            if (differs(login.getBrowser(), seen.browser()) || differs(login.getOs(), seen.os())) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("sessionId", event.sessionId().toString());
                evidence.put("loginBrowser", login.getBrowser());
                evidence.put("seenBrowser", seen.browser());
                evidence.put("loginOs", login.getOs());
                evidence.put("seenOs", seen.os());
                evidence.put("ip", event.ipAddress());
                evidence.put("userAgent", truncate(event.userAgent()));
                evidence.put("windowFrom", from.toString());
                evidence.put("windowTo", to.toString());
                byUser.put(event.userId(), new IncidentRequest(
                        event.companyId(), event.userId(), IncidentRule.SESSION_AGENT_CHANGE, evidence));
            }
        }
        return List.copyOf(byUser.values());
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_UA_LENGTH ? value : value.substring(0, MAX_UA_LENGTH);
    }

    private static boolean differs(String login, String seen) {
        return known(login) && known(seen) && !login.trim().equalsIgnoreCase(seen.trim());
    }

    private static boolean known(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value.trim());
    }
}
