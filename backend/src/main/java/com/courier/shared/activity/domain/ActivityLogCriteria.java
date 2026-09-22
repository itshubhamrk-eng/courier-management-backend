package com.courier.shared.activity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Every field optional except {@code companyId}, which the service always binds itself
 * from the caller's context — see {@code ActivityLogService#search}. Never trust a
 * caller-supplied company id here; that is exactly the isolation hole decision 27
 * (AI_CONTEXT.md) already closed for every other module's search criteria.
 */
public record ActivityLogCriteria(
        UUID companyId,
        UUID userId,
        String module,
        String action,
        String entityType,
        String entityId,
        ActivityStatus status,
        UUID sessionId,
        Instant dateFrom,
        Instant dateTo,
        String search
) {
}
