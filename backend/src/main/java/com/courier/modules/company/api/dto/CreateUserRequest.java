package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/users}. {@code COMPANY_ADMIN}, own company only.
 *
 * <p>Not accepted: {@code companyId} (from the JWT), {@code status}/{@code isLocked}
 * (lifecycle endpoints). {@code password} is optional — omit it and the account is
 * created PENDING with an unusable password an admin later resets. {@code roleIds} is
 * optional — the company's default role is applied when empty.
 */
@Schema(name = "CreateUserRequest", description = "New user within the caller's company")
public record CreateUserRequest(

        @Size(max = 50)
        @Schema(description = "HR key, unique per company, uppercased. Immutable.", example = "EMP001")
        String employeeCode,

        @Size(max = 50) String employeeId,

        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @Size(max = 100) String lastName,
        @Size(max = 150) String displayName,

        @NotBlank @Email @Size(max = 255) String email,

        @Size(max = 100)
        @Pattern(regexp = "^$|^[A-Za-z0-9._-]{3,100}$",
                message = "must be 3-100 chars of letters, digits, dot, underscore or hyphen (stored lowercased)")
        @Schema(description = "Login handle, globally unique", example = "asha.nair")
        String username,

        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String mobile,

        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String alternateMobile,

        @Size(min = 8, max = 72)
        @Schema(description = "Optional. Omit for a PENDING account with a reset-only password.")
        String password,

        Gender gender,

        @Past LocalDate dateOfBirth,

        @Size(max = 100) String designation,
        @Size(max = 100) String department,

        LocalDate joiningDate,

        @Schema(description = "Another user of the same company") UUID reportingManagerId,
        @Schema(description = "Branch placement — a user belongs to at most one") UUID branchId,
        @Schema(description = "Hub placement — a user belongs to at most one") UUID hubId,

        @Size(max = 500) String profileImage,
        @Size(max = 500) String remarks,

        @Schema(description = "Company roles to assign. Empty applies the default role.")
        List<UUID> roleIds
) {
}
