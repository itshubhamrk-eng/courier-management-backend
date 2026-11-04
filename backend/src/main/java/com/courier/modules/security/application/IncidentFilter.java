package com.courier.modules.security.application;

import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.IncidentSeverity;
import com.courier.modules.security.domain.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

/** Optional criteria for listing incidents; any null field is ignored. */
public record IncidentFilter(
        IncidentStatus status,
        IncidentSeverity severity,
        IncidentRule rule,
        UUID userId,
        Instant from,
        Instant to) {
}
