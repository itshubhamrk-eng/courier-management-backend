package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact projection for list responses. Drops the HR detail a directory view does not
 * render; clients fetch {@code GET /users/{id}} for the rest.
 */
@Schema(name = "UserSummaryResponse", description = "Company user, list projection")
public record UserSummaryResponse(
        UUID id,
        UUID companyId,
        String employeeCode,
        String displayName,
        String email,
        String username,
        String mobile,
        String designation,
        String department,
        UUID branchId,
        UUID hubId,
        UserStatus status,
        boolean isLocked,
        int roleCount,
        Instant createdDate,
        Long version
) {
}
