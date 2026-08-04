package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of a grant, and the role's resulting permissions.
 *
 * <p>The four lists are reported separately because "it worked" is not the whole truth:
 * a permission outside the company's plan is silently useless if the response only says
 * how many were saved. {@code rejected} is the field a UI must surface.
 */
@Schema(name = "RolePermissionResponse", description = "Result of assigning permissions to a role")
public record RolePermissionResponse(

        UUID roleId,
        String roleCode,

        @Schema(description = "Newly added")
        List<String> granted,

        @Schema(description = "Removed, when replacing the set")
        List<String> revoked,

        @Schema(description = "Already held, so not re-added")
        List<String> skipped,

        @Schema(description = "Refused: inactive, or outside the company's subscription plan")
        List<String> rejected,

        @Schema(description = "Everything the role holds after this call")
        List<String> effectivePermissions
) {
}
