package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/departments}. {@code COMPANY_ADMIN} only, own company.
 *
 * <p>{@code companyId} and {@code status} are not accepted — the company comes from the
 * verified JWT, and a new department is always {@code ACTIVE}. {@code roleIds} may be
 * empty: a department with no role yet grants nothing until one is added.
 */
@Schema(name = "CreateDepartmentRequest", description = "New department within the caller's company")
public record CreateDepartmentRequest(

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{1,48}[A-Za-z0-9]$",
                message = "must be 3-50 characters of letters, digits, space, hyphen or underscore")
        @Schema(description = "Stable key, uppercased and spaces replaced with underscores on "
                + "save. Immutable afterwards.", example = "OPERATIONS")
        String departmentCode,

        @NotBlank @Size(max = 100) @Schema(example = "Operations")
        String departmentName,

        @Size(max = 255) @Schema(example = "Booking, dispatch and delivery staff.")
        String description,

        @Schema(description = "Company roles this department offers to a user placed in it. "
                + "May be empty.")
        List<UUID> roleIds
) {
}
