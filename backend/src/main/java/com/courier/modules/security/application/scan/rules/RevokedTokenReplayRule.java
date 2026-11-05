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
 * Repeated use of credentials for a session that is already dead.
 *
 * <p>What it measures: the number of {@code SESSION_EXPIRED} rows in {@code login_history}
 * per user inside the window, i.e. refresh attempts against a session that was revoked or
 * expired ({@code AuthService#refresh} records one each time). It fires when a user has MORE
 * than {@link SecurityProperties#getRevokedReplayThreshold()} of them.
 *
 * <p>What it does NOT measure: rejected revoked <em>access</em> tokens. The JWT filter only
 * logs those at debug level and persists nothing, and this rule intentionally adds no new
 * per-request write to change that.
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
        return loginHistoryRepository.findUsersWithSessionExpiredAbove(from, to, threshold).stream()
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
