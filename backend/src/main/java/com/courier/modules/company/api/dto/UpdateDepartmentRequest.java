package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/departments/{id}}.
 *
 * <p>Full replacement of the editable fields, including the role grants: {@code roleIds}
 * is the complete set the department should hold afterwards, not a delta. {@code
 * departmentCode} is immutable — user records and audit rows reference it.
 */
@Schema(name = "UpdateDepartmentRequest", description = "Full replacement of a department's editable fields")
public record UpdateDepartmentRequest(

        @NotBlank @Size(max = 100) String departmentName,

        @Size(max = 255) String description,

        @Schema(description = "Complete set of roles this department should offer afterwards.")
        List<UUID> roleIds,

        @NotNull
        @PositiveOrZero
        @Schema(description = "Version last read by the client. A stale value returns 409.",
                example = "1")
        Long version
) {
}
