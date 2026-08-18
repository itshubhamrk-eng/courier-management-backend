package com.courier.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ImpersonateRequest",
        description = "Step-up confirmation: the SUPER_ADMIN's own current password, "
                + "re-entered right before switching into a company's context.")
public record ImpersonateRequest(

        @NotBlank(message = "Your password is required to confirm this action")
        String password
) {
}
