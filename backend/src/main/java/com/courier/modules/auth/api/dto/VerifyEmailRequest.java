package com.courier.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "VerifyEmailRequest", description = "Confirm an email address")
public record VerifyEmailRequest(

        @NotBlank(message = "Token is required")
        @Schema(description = "The single-use token from the verification link")
        String token
) {
}
