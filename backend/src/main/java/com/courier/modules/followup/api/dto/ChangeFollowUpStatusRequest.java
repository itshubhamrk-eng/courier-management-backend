package com.courier.modules.followup.api.dto;

import com.courier.modules.followup.domain.FollowUpStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeFollowUpStatusRequest(@NotNull FollowUpStatus status, String remarks) {
}
