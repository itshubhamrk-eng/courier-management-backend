package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;
import com.courier.modules.company.domain.PermissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Query parameters of {@code GET /api/v1/permissions}, bound as a parameter object.
 * Paging and sorting come from Spring's {@code Pageable} on the same query string.
 */
@Schema(name = "PermissionSearchRequest", description = "Permission catalogue filters")
public record PermissionSearchRequest(

        @Schema(description = "Repeatable, e.g. `module=SHIPMENT&module=TRACKING`")
        Set<PermissionModule> module,

        @Schema(description = "Repeatable, e.g. `action=CREATE&action=UPDATE`")
        Set<PermissionAction> action,

        PermissionStatus status,

        @Schema(description = "true for seeded permissions, false for custom ones")
        Boolean isSystemPermission,

        @Size(max = 60) String resource,

        @Schema(description = "Only permissions that depend on a subscription feature")
        Boolean planGatedOnly,

        @Size(max = 100)
        @Schema(description = "Free text over code, name, description and resource. "
                + "`%`, `_` and `\\` match themselves — permission codes contain underscores.")
        String search
) {

    public static PermissionSearchRequest empty() {
        return new PermissionSearchRequest(null, null, null, null, null, null, null);
    }
}
