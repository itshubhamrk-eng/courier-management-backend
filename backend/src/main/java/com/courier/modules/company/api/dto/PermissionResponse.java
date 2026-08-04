package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;
import com.courier.modules.company.domain.PermissionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * A permission as the catalogue exposes it.
 *
 * <p>Nulls are serialised rather than dropped, so a client can tell "no feature flag"
 * from "field missing"; the global Jackson setting omits nulls.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PermissionResponse", description = "A grantable right")
public record PermissionResponse(

        UUID id,
        String permissionCode,
        String permissionName,
        PermissionModule module,
        String resource,
        PermissionAction action,
        String description,

        @Schema(description = "Seeded by the platform: read-only, never deletable")
        boolean isSystemPermission,

        PermissionStatus status,
        Integer displayOrder,

        @Schema(description = "Subscription feature required, or null when unconditional")
        String requiredFeatureFlag,

        UUID createdBy,
        Instant createdDate,
        UUID updatedBy,
        Instant updatedDate,
        Long version
) {
}
