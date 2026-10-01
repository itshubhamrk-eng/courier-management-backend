package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "AssignHubRequest", description = "The hub branch to assign; null clears it")
public record AssignHubRequest(UUID hubId) {
}
