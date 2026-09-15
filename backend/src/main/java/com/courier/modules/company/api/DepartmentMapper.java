package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.CreateDepartmentRequest;
import com.courier.modules.company.api.dto.DepartmentResponse;
import com.courier.modules.company.api.dto.DepartmentRoleSummary;
import com.courier.modules.company.api.dto.UpdateDepartmentRequest;
import com.courier.modules.company.application.command.CreateDepartmentCommand;
import com.courier.modules.company.application.command.UpdateDepartmentCommand;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.Department;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Comparator;

/** Translates between the wire contract and the application/domain types. Hand-written,
 *  like the project's other mappers — see {@code RoleMapper}'s own note. */
@Component
public class DepartmentMapper {

    public CreateDepartmentCommand toCommand(CreateDepartmentRequest request) {
        return new CreateDepartmentCommand(
                request.departmentCode(), request.departmentName(), request.description(), request.roleIds());
    }

    public UpdateDepartmentCommand toCommand(UpdateDepartmentRequest request) {
        return new UpdateDepartmentCommand(
                request.departmentName(), request.description(), request.roleIds(), request.version());
    }

    /** @param roles what the department currently offers, fetched by the caller from
     *               {@code DepartmentService.rolesOf} — grants live in their own table. */
    public DepartmentResponse toResponse(Department department, List<CompanyRole> roles) {
        List<DepartmentRoleSummary> roleSummaries = roles == null ? List.of() : roles.stream()
                .sorted(Comparator.comparing(CompanyRole::getRoleCode))
                .map(r -> new DepartmentRoleSummary(r.getId(), r.getRoleCode(), r.getRoleName()))
                .toList();

        return new DepartmentResponse(
                department.getId(),
                department.getCompanyId(),
                department.getDepartmentCode(),
                department.getDepartmentName(),
                department.getDescription(),
                department.getStatus(),
                roleSummaries,
                department.getCreatedBy(),
                department.getCreatedAt(),
                department.getUpdatedBy(),
                department.getUpdatedAt(),
                department.getVersion());
    }

    public DepartmentResponse toResponse(Department department) {
        return toResponse(department, List.of());
    }
}
