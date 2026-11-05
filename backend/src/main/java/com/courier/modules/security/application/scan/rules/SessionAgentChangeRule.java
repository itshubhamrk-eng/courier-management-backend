package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserSessionRepository;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.scan.ScanRule;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.SessionFingerprint;
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
 * A session whose requests carry a different browser or OS than the one recorded when it
 * logged in — a token being used from a different client than it was issued to.
 *
 * <p>Unknown/blank values on either side are never treated as a change: the parser reports
 * "Unknown" for user agents it cannot classify, which says nothing about a switch.
 */
@Component
@RequiredArgsConstructor
public class SessionAgentChangeRule implements ScanRule {

    private final ActivityLogRepository activityLogRepository;
    private final UserSessionRepository userSessionRepository;

    @Override
    public IncidentRule code() {
        return IncidentRule.SESSION_AGENT_CHANGE;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        List<SessionFingerprint> seen = activityLogRepository.findSessionFingerprints(from, to);
        if (seen.isEmpty()) {
            return List.of();
        }
        Set<UUID> sessionIds = seen.stream().map(SessionFingerprint::sessionId).collect(Collectors.toSet());
        Map<UUID, UserSession> sessions = userSessionRepository.findAllById(sessionIds).stream()
                .collect(Collectors.toMap(UserSession::getId, Function.identity(), (a, b) -> a));

        // One request per user: the first mismatching session is evidence enough.
        Map<UUID, IncidentRequest> byUser = new LinkedHashMap<>();
        for (SessionFingerprint fp : seen) {
            UserSession login = sessions.get(fp.sessionId());
            if (login == null || byUser.containsKey(fp.userId())) {
                continue;
            }
            if (differs(login.getBrowser(), fp.browser()) || differs(login.getOs(), fp.os())) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("sessionId", fp.sessionId().toString());
                evidence.put("loginBrowser", login.getBrowser());
                evidence.put("seenBrowser", fp.browser());
                evidence.put("loginOs", login.getOs());
                evidence.put("seenOs", fp.os());
                evidence.put("windowFrom", from.toString());
                evidence.put("windowTo", to.toString());
                byUser.put(fp.userId(),
                        new IncidentRequest(fp.companyId(), fp.userId(), IncidentRule.SESSION_AGENT_CHANGE, evidence));
            }
        }
        return List.copyOf(byUser.values());
    }

    private static boolean differs(String login, String seen) {
        return known(login) && known(seen) && !login.trim().equalsIgnoreCase(seen.trim());
    }

    private static boolean known(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value.trim());
    }
}
