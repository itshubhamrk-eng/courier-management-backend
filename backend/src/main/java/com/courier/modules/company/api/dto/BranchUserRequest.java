package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The branch's operating user, supplied on the same form as the branch itself.
 *
 * <p>Every field is optional, including the block as a whole: a branch created without one
 * still gets a user, with an address derived from its code. What the block buys is a real
 * address and a real name instead of a derived one.
 *
 * <p>{@code password} is the administrator's choice. Left blank, the server generates one
 * and returns it in the create response — the only time it is ever readable.
 */
@Schema(name = "BranchUserRequest",
        description = "Login account created together with the branch")
public record BranchUserRequest(

        @Email @Size(max = 255)
        @Schema(description = "Login address. Defaults to the branch email, then to a "
                + "derived <branch-code>@<company-code>.local",
                example = "latur@legacy.test") String email,

        @Size(max = 100) @Schema(example = "Latur") String firstName,

        @Size(max = 100) @Schema(example = "Branch") String lastName,

        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        @Schema(description = "Defaults to the branch mobile") String mobile,

        // No minimum length here on purpose: the policy owns that rule and its message
        // names the one that failed. A second number in this file would drift from it.
        @Size(max = 128)
        @Schema(description = "Must satisfy the password policy. Blank means "
                + "\"generate one and return it once\"") String password
) {
}
