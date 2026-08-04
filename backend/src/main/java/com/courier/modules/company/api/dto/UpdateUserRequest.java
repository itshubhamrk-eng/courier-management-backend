package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/users/{id}}. Full replacement of the editable profile.
 *
 * <p>Absent by design: {@code email}, {@code username}, {@code employeeCode} (identity);
 * {@code status}/{@code isLocked}/{@code password} (their own endpoints). Branch and hub
 * are accepted here for a bulk edit and also have dedicated assignment endpoints.
 */
@Schema(name = "UpdateUserRequest", description = "Full replacement of a user's profile")
public record UpdateUserRequest(

        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @Size(max = 100) String lastName,
        @Size(max = 150) String displayName,

        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String mobile,

        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String alternateMobile,

        Gender gender,
        @Past LocalDate dateOfBirth,
        @Size(max = 100) String designation,
        @Size(max = 100) String department,
        LocalDate joiningDate,
        UUID reportingManagerId,
        UUID branchId,
        UUID hubId,
        @Size(max = 500) String profileImage,
        @Size(max = 500) String remarks,

        @NotNull @PositiveOrZero
        @Schema(description = "Version last read by the client. A stale value returns 409.")
        Long version
) {
}
