package com.courier.modules.followup.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleRequest(@NotNull Instant newDueDate, String reason) {
}
