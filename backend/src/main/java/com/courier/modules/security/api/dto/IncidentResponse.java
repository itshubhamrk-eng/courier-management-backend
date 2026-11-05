package com.courier.modules.security.api.dto;

import com.courier.modules.security.domain.AutoAction;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.IncidentSeverity;
import com.courier.modules.security.domain.IncidentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One security incident with the people involved resolved to names. User, role and branch
 * fields are null/empty when the incident has no user (IP-level) or the user is gone.
 */
@Schema(description = "A detected security incident and the decision taken on it")
public record IncidentResponse(
        UUID id,
        UUID companyId,
        IncidentRule rule,
        IncidentSeverity severity,
        IncidentStatus status,
        AutoAction autoAction,
        Instant detectedAt,
        UUID userId,
        String userDisplayName,
        String userEmail,
        List<String> userRoles,
        String branchName,
        @Schema(description = "What the rule saw, as a JSON object; {} when absent or unreadable")
        JsonNode evidence,
        @Schema(description = "True when stored evidence could not be parsed (evidence is then {})")
        boolean evidenceUnreadable,
        UUID resolvedBy,
        @Schema(description = "Display name of the resolver, null when the account no longer resolves")
        String resolvedByName,
        Instant resolvedAt,
        String resolutionNote
) {
}
