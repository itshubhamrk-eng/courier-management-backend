package com.courier.shared.activity.domain;

import java.util.UUID;

/** A session and how many distinct client IPs its activity logs show inside a scan window. */
public record SessionIpSummary(UUID companyId, UUID userId, UUID sessionId, long distinctIpCount) {
}
