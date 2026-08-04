package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/permissions/{id}}. {@code SUPER_ADMIN} only, and refused
 * outright for a system permission.
 *
 * <p>Module and action are absent: together they are the immutable code that roles,
 * grants and {@code @PreAuthorize} expressions all reference. Only presentation and
 * plan gating may change.
 */
@Schema(name = "UpdatePermissionRequest", description = "Edit a custom permission")
public record UpdatePermissionRequest(

        @NotBlank @Size(max = 150) String permissionName,

        @Size(max = 60)
        @Pattern(regexp = "^$|^[a-z0-9-]+$", message = "must be lowercase, digits and hyphens")
        String resource,

        @Size(max = 255) String description,

        @Min(0) Integer displayOrder,

        @Size(max = 50)
        @Schema(description = "Null clears the gating, making the permission unconditional")
        String requiredFeatureFlag,

        @NotNull
        @PositiveOrZero
        @Schema(description = "Version last read by the client. A stale value returns 409.")
        Long version
) {
}
