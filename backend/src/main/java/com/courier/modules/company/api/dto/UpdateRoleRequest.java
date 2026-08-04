package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.RoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/roles/{id}}.
 *
 * <p>A full replacement of the editable fields; an omitted optional field is written as
 * null. {@code roleCode} is immutable — users and audit rows reference it, so
 * re-pointing it would rewrite history.
 *
 * <p>A **system role may be edited** here: renaming `Company Admin` to `Owner` is a
 * legitimate thing for a company to want. What it may never be is deleted.
 */
@Schema(name = "UpdateRoleRequest", description = "Full replacement of a role's editable fields")
public record UpdateRoleRequest(

        @NotBlank @Size(max = 100) String roleName,

        @Size(max = 255) String description,

        @NotNull RoleType roleType,

        @Schema(description = "Promoting a role demotes the previous default")
        Boolean isDefault,

        /*
         * Mandatory. Without the version the client last read, two admins editing the
         * same role would both succeed and the second would silently discard the first.
         */
        @NotNull
        @PositiveOrZero
        @Schema(description = "Version last read by the client. A stale value returns 409.",
                example = "1")
        Long version
) {
}
