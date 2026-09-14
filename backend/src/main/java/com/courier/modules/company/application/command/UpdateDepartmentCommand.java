package com.courier.modules.company.application.command;

import java.util.List;
import java.util.UUID;

/**
 * Input to {@code DepartmentService.update}. Full replacement of the editable fields,
 * including the role grants — {@code roleIds} is the complete set the department should
 * hold afterwards, not a delta. {@code departmentCode} is immutable and absent.
 */
public record UpdateDepartmentCommand(
        String departmentName,
        String description,
        List<UUID> roleIds,
        Long expectedVersion
) {
}
