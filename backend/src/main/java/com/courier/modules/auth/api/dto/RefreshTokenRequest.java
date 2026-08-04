package com.courier.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest", description = "Exchange a refresh token for a new pair")
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        @Schema(description = "The refresh token from the previous login or refresh. "
                + "Single use: it is revoked as soon as it is redeemed.")
        String refreshToken
) {
}
