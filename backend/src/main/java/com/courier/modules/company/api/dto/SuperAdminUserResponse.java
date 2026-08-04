package com.courier.modules.company.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A platform operator.
 *
 * <p>{@code temporaryPassword} is populated only in the response to a creation, and
 * only when the server generated it — a password the caller typed is not echoed back.
 * Like the company admin's, it is readable exactly once and is never logged, audited or
 * retrievable again.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "SuperAdminUserResponse", description = "A platform-tier account")
public record SuperAdminUserResponse(

        UUID id,

        @Schema(description = "The company this row is anchored to for storage only")
        UUID homeCompanyId,

        String email,
        String firstName,
        String lastName,
        String phone,
        String status,
        boolean emailVerified,

        @Schema(description = "Platform-tier roles held, e.g. SUPER_ADMIN")
        List<String> roles,

        Instant lastLoginAt,
        Instant createdAt,

        @Schema(description = "Shown once, on creation, and only when generated. "
                + "Never retrievable again.")
        String temporaryPassword) {
}
