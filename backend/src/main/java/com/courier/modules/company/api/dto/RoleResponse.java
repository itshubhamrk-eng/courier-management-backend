package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.RoleType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full representation of a role.
 *
 * <p>Nulls are serialised rather than dropped, so a client can tell "not set" from
 * "field missing"; the global Jackson setting omits nulls, hence {@code ALWAYS} here.
 *
 * <p>{@code permissions} is read-only in this module — Permission management is a
 * separate one. It is included because a roles screen that cannot show what a role
 * actually grants is not much of a roles screen.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "RoleResponse", description = "Company role in full")
public record RoleResponse(

        UUID id,

        @Schema(description = "The owning company's tenancy key")
        UUID companyId,

        String roleCode,
        String roleName,
        String description,
        RoleType roleType,

        @Schema(description = "Seeded by the platform: editable, never deletable")
        boolean isSystemRole,

        @Schema(description = "Assigned to new users when none is specified")
        boolean isDefault,

        RoleStatus status,

        @Schema(description = "Read-only here; granted by the Permission module")
        List<String> permissions,

        UUID createdBy,
        Instant createdDate,
        UUID updatedBy,
        Instant updatedDate,

        @Schema(description = "Echo this back in a PUT to detect concurrent edits")
        Long version
) {
}
