package com.courier.shared.activity.domain;

import java.util.UUID;

/** One distinct (session, browser, os) combination seen in activity logs inside a scan window. */
public record SessionFingerprint(UUID companyId, UUID userId, UUID sessionId, String browser, String os) {
}
