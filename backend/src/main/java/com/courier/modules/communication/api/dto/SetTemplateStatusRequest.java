package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.TemplateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "SetTemplateStatusRequest", description = "ACTIVE = sends on its event; INACTIVE = never sends")
public record SetTemplateStatusRequest(@NotNull TemplateStatus status) {
}
