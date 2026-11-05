package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.UserEventCount;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.application.scan.ScanRule;
import com.courier.modules.security.domain.IncidentRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repeated use of credentials for a session that was explicitly revoked.
 *
 * <p>What it measures: {@code SESSION_EXPIRED} rows in {@code login_history} (written by
 * {@code AuthService#refresh} each time a valid refresh token is presented for an inactive
 * session) per user inside the window, counting ONLY sessions whose {@code user_sessions.
 * revoked_at} is set. Sessions that merely aged out are not counted. Fires when the count is
 * strictly greater than {@link SecurityProperties#getRevokedReplayThreshold()} per scan
 * window.
 *
 * <p>What it does NOT measure: rejected revoked <em>access</em> tokens. The JWT filter only
 * logs those at debug level and persists nothing, and this rule intentionally adds no new
 * per-request write. Reuse of a rotated refresh token is the REFRESH_TOKEN_REUSE rule.
 */
@Component
@RequiredArgsConstructor
public class RevokedTokenReplayRule implements ScanRule {

    private final LoginHistoryRepository loginHistoryRepository;
    private final SecurityProperties properties;

    @Override
    public IncidentRule code() {
        return IncidentRule.REVOKED_TOKEN_REPLAY;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        int threshold = properties.getRevokedReplayThreshold();
        return loginHistoryRepository.findUsersWithRevokedSessionRefreshAbove(from, to, threshold).stream()
                .map(row -> toRequest(row, threshold, from, to))
                .toList();
    }

    private static IncidentRequest toRequest(UserEventCount row, int threshold, Instant from, Instant to) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rejections", row.count());
        evidence.put("threshold", threshold);
        evidence.put("windowFrom", from.toString());
        evidence.put("windowTo", to.toString());
        return new IncidentRequest(row.companyId(), row.userId(), IncidentRule.REVOKED_TOKEN_REPLAY, evidence);
    }
}
