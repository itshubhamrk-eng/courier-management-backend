package com.courier.shared.activity.domain;

import java.util.UUID;

/** {@code activity_logs} rows of one user grouped by (request method, endpoint template) over a window. */
public record UserEndpointCount(UUID companyId, UUID userId, String requestMethod, String apiEndpoint, long count) {
}
