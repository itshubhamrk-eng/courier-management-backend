package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of assigning users to a branch: what was placed, what was already there, and
 * what did not resolve to a user of this company.
 */
@Schema(name = "AssignUsersResponse", description = "Result of assigning users to a branch")
public record AssignUsersResponse(
        List<UUID> assigned, List<UUID> skipped, List<UUID> rejected
) {
}
