package com.courier.modules.security.application;

import com.courier.modules.security.domain.IncidentRule;

import java.util.Map;
import java.util.UUID;

/** What a detector hands to {@link SecurityIncidentService#raise}. */
public record IncidentRequest(UUID companyId, UUID userId, IncidentRule rule, Map<String, Object> evidence) {
}
