package com.courier.modules.followup.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AddFollowUpNoteRequest(@NotBlank String note) {
}
