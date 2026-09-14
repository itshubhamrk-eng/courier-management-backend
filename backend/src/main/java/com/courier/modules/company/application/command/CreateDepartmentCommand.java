package com.courier.modules.company.application.command;

import java.util.List;
import java.util.UUID;

/**
 * Input to {@code DepartmentService.create}. {@code roleIds} may be empty — a department
 * with no role yet grants nothing, and roles can be added afterwards.
 */
public record CreateDepartmentCommand(
        String departmentCode,
        String departmentName,
        String description,
        List<UUID> roleIds
) {
}
