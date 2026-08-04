package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Body of the branch and hub assignment endpoints. A null id clears the placement, which
 * is how a user is moved out of a branch or hub without deleting them.
 */
@Schema(name = "AssignPlacementRequest", description = "Branch or hub to place the user at; null clears it")
public record AssignPlacementRequest(UUID id) {
}
