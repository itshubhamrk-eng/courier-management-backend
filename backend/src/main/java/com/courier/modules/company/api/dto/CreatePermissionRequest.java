package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/permissions}. {@code SUPER_ADMIN} only.
 *
 * <p>{@code permissionCode} is not accepted: it is derived as {@code MODULE_ACTION}, so a
 * code that disagreed with its own module and action is impossible. Neither is
 * {@code isSystemPermission} — only the seeding migration creates system permissions,
 * and a settable flag would mint an undeletable row by accident.
 */
@Schema(name = "CreatePermissionRequest", description = "New custom permission")
public record CreatePermissionRequest(

        @NotNull
        @Schema(description = "Functional area. The code becomes MODULE_ACTION.",
                example = "SHIPMENT")
        PermissionModule module,

        @NotNull
        @Schema(example = "APPROVE")
        PermissionAction action,

        @Size(max = 150)
        @Schema(description = "Defaults to a readable form of module + action",
                example = "Approve Shipments")
        String permissionName,

        @Size(max = 60)
        @Pattern(regexp = "^$|^[a-z0-9-]+$", message = "must be lowercase, digits and hyphens")
        @Schema(description = "URL spelling of the resource. Defaults from the module.",
                example = "shipments")
        String resource,

        @Size(max = 255) String description,

        @Min(0)
        @Schema(description = "Sort key. Defaults to the module's base plus the action's offset.")
        Integer displayOrder,

        @Size(max = 50)
        @Schema(description = "Subscription feature this right depends on. A plan without "
                + "it cannot grant this permission to any role.",
                example = "bulkBooking")
        String requiredFeatureFlag
) {
}
