package com.courier.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LogoutRequest", description = "End the current session or all sessions")
public record LogoutRequest(

        @Schema(description = "Refresh token of the session to end. Omit to only "
                + "denylist the current access token.")
        String refreshToken,

        @Schema(description = "Revoke every session and refresh token for this user",
                example = "false")
        boolean allDevices
) {
}
