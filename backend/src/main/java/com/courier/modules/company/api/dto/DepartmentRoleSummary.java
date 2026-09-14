package com.courier.modules.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** One role a department offers — just enough for a picker, not the full {@code RoleSummaryResponse}. */
@Schema(name = "DepartmentRoleSummary", description = "A role offered by a department")
public record DepartmentRoleSummary(UUID id, String roleCode, String roleName) {
}
