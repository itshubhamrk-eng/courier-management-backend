package com.courier.modules.followup.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignFollowUpRequest(@NotNull UUID assignedUserId, String remarks) {
}
