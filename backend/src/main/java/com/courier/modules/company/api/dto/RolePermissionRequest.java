package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /api/v1/roles/{roleId}/permissions}.
 *
 * <p>Bulk by design: a permission matrix submits the whole set, and applying it as one
 * transaction stops a role sitting half-configured between calls.
 *
 * @param permissionCodes codes, not ids — a matrix screen knows `SHIPMENT_CREATE`, and
 *                        codes are stable while ids are not portable between environments
 * @param replaceExisting true makes the role hold exactly this set, revoking the rest —
 *                        what a "save" button means. False only adds.
 */
@Schema(name = "RolePermissionRequest", description = "Permissions to grant to a role")
public record RolePermissionRequest(

        @NotEmpty
        @Size(max = 500, message = "at most 500 permissions may be assigned in one request")
        @Schema(example = "[\"SHIPMENT_CREATE\", \"SHIPMENT_READ\", \"TRACKING_CREATE\"]")
        List<String> permissionCodes,

        @Schema(description = "Replace the role's permissions with exactly this set",
                defaultValue = "false")
        Boolean replaceExisting
) {

    public boolean replace() {
        return Boolean.TRUE.equals(replaceExisting);
    }
}
