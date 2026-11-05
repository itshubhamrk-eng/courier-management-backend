package com.courier.modules.auth.domain;

import java.util.UUID;

/** Per-user event total from a grouped {@code login_history} query. */
public record UserEventCount(UUID companyId, UUID userId, long count) {
}
