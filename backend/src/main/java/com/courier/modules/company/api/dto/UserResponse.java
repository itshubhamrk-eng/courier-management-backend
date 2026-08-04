package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.Gender;
import com.courier.modules.company.domain.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full representation of a user. Never carries the password hash. Nulls are serialised,
 * so a client can tell "not set" from "field missing".
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "UserResponse", description = "Company user in full")
public record UserResponse(

        UUID id,
        UUID companyId,
        String employeeCode,
        String employeeId,
        String firstName,
        String middleName,
        String lastName,
        String displayName,
        String email,
        String username,
        String mobile,
        String alternateMobile,
        Gender gender,
        LocalDate dateOfBirth,
        String designation,
        String department,
        LocalDate joiningDate,
        UUID reportingManagerId,
        UUID branchId,
        UUID hubId,
        String profileImage,
        UserStatus status,
        boolean isLocked,
        Instant lastLogin,
        int failedLoginCount,
        String remarks,

        @Schema(description = "Company role codes this user holds")
        List<String> roles,

        UUID createdBy,
        Instant createdDate,
        UUID updatedBy,
        Instant updatedDate,
        Long version,

        @Schema(description = "Present only in a creation response: true when the password "
                + "was auto-generated and the account is PENDING until reset")
        Boolean passwordGenerated
) {
}
