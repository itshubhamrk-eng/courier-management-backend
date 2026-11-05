package com.courier.modules.auth.domain;

import java.util.UUID;

/**
 * One refresh-token issuance (login or rotation) for a session: where it came from and with
 * which user agent. Input to the session-anomaly scan rules.
 */
public record SessionRefreshEvent(UUID companyId, UUID userId, UUID sessionId, String userAgent, String ipAddress) {
}
