package com.courier.shared.activity.domain;

import java.util.UUID;

/** {@code activity_logs} rows of one user grouped by (module, action) over a window. */
public record UserActionCount(UUID companyId, UUID userId, String module, String action, long count) {
}
