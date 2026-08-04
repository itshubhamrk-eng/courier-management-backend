package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create another platform operator.
 *
 * <p>The role is not a field: this endpoint creates {@code SUPER_ADMIN} accounts and
 * nothing else. A role parameter here would be a way to mint any account on the
 * platform from the one endpoint that needs the least surface.
 */
@Schema(name = "CreateSuperAdminRequest", description = "A new platform operator (SUPER_ADMIN)")
public record CreateSuperAdminRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 255)
        @Schema(description = "Login address. Must be unused across the whole platform, "
                + "not merely within one company — a platform operator signs in with no "
                + "company code, so a duplicate would make that lookup ambiguous.")
        String email,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Pattern(regexp = "^$|^[+]?[0-9 ()-]{7,20}$", message = "Phone number is not valid")
        @Size(max = 20)
        String phone,

        @Size(min = 8, max = 128)
        @Schema(description = "Optional. Omit and one is generated and returned once in "
                + "the response. Either way it is checked against the password policy.")
        String password,

        @Schema(description = "Company the row is anchored to for storage; `users` has a "
                + "non-null ownership column. It confers nothing — a super admin already "
                + "reaches every company. Defaults to the caller's own anchor.")
        UUID homeCompanyId) {
}
