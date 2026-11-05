package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.RefreshTokenRepository;
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
 * Repeated presentation of a revoked refresh token that was revoked WITHOUT being rotated
 * (logout, session eviction, ban, ...).
 *
 * <p>What it measures: per user inside the window, the REFRESH_TOKEN_REUSE_DETECTED audit
 * rows {@code TokenIssuer} writes whenever an already-revoked refresh token is presented
 * again, restricted to tokens with no rotation successor ({@code replaced_by_jti} null);
 * user and company are taken from the replayed token (audit {@code entity_id}). Fires when
 * the count is strictly greater than {@link SecurityProperties#getRevokedReplayThreshold()}
 * per scan window. Replays of rotated tokens are the auto-ban REFRESH_TOKEN_REUSE rule's
 * business and are excluded to avoid double counting.
 *
 * <p>What it does NOT measure: rejected revoked <em>access</em> tokens. The JWT filter only
 * logs those at debug level and persists nothing, and this rule intentionally adds no new
 * per-request write.
 *
 * <p>Note on the earlier login_history SESSION_EXPIRED approach: it was dropped because every
 * session-revoke path also revokes the session's refresh tokens, so a refresh after a revoke
 * is rejected in {@code TokenIssuer.rotate} and never reaches {@code recordSessionExpired}.
 */
@Component
@RequiredArgsConstructor
public class RevokedTokenReplayRule implements ScanRule {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties properties;

    @Override
    public IncidentRule code() {
        return IncidentRule.REVOKED_TOKEN_REPLAY;
    }

    @Override
    public List<IncidentRequest> scan(Instant from, Instant to) {
        int threshold = properties.getRevokedReplayThreshold();
        return refreshTokenRepository.findUsersWithRevokedTokenReplaysAbove(from, to, threshold).stream()
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
