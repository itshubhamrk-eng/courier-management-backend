package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/roles}.
 *
 * <p>Four fields a caller might expect are deliberately not accepted:
 * <ul>
 *   <li>{@code companyId} — taken from the verified JWT. Accepting it would let a company
 *       admin create a role inside someone else's company.</li>
 *   <li>{@code isSystemRole} — only the platform seeds system roles; a settable flag
 *       would let a company mint itself an undeletable one.</li>
 *   <li>{@code status} — a new role is always ACTIVE; deactivation has its own endpoint
 *       so the change is separately audited.</li>
 *   <li>{@code permissions} — Permission management is a separate module.</li>
 * </ul>
 */
@Schema(name = "CreateRoleRequest", description = "New role within the caller's company")
public record CreateRoleRequest(

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{1,48}[A-Za-z0-9]$",
                message = "must be 3-50 characters of letters, digits, space, hyphen or underscore")
        @Schema(description = "Stable key, uppercased and spaces replaced with underscores "
                + "on save. Immutable afterwards.",
                example = "NIGHT_SHIFT_SUPERVISOR")
        String roleCode,

        @NotBlank
        @Size(max = 100)
        @Schema(example = "Night Shift Supervisor")
        String roleName,

        @Size(max = 255)
        @Schema(example = "Runs the hub between 22:00 and 06:00.")
        String description,

        @NotNull
        @Schema(description = "Functional grouping. Note this is not system-vs-custom — "
                + "that is `isSystemRole`.",
                example = "OPERATIONS")
        RoleType roleType,

        @Schema(description = "Assign this role to new users when none is specified. "
                + "Promoting a role demotes whichever one currently holds the flag.",
                example = "false")
        Boolean isDefault
) {
}
