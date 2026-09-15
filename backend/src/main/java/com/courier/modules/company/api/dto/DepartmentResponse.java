package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.DepartmentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full representation of a department, including the roles it currently offers — a
 * department screen that cannot show what it grants is not much of one, the same reason
 * {@code RoleResponse} carries its permissions.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "DepartmentResponse", description = "Company department in full")
public record DepartmentResponse(

        UUID id,
        UUID companyId,
        String departmentCode,
        String departmentName,
        String description,
        DepartmentStatus status,

        List<DepartmentRoleSummary> roles,

        UUID createdBy,
        Instant createdDate,
        UUID updatedBy,
        Instant updatedDate,

        @Schema(description = "Echo this back in a PUT to detect concurrent edits")
        Long version
) {
}
