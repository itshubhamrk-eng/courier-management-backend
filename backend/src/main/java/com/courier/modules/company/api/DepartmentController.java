package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.CreateDepartmentRequest;
import com.courier.modules.company.api.dto.DepartmentResponse;
import com.courier.modules.company.api.dto.UpdateDepartmentRequest;
import com.courier.modules.company.application.DepartmentService;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.Department;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Departments within a company, and the roles each one offers.
 *
 * <p>Who may do what is enforced on {@code DepartmentService}, not here: {@code
 * COMPANY_ADMIN} manages their own company's departments; {@code SUPER_ADMIN} may read
 * across every company; {@code BRANCH_MANAGER} may read the assignable list to place
 * their own new hires, the same bridge {@code RoleController} grants for role pickers.
 *
 * <p>Unpaged throughout, like {@code RoleController.assignable} — a company has a handful
 * of departments, not a catalogue to page through.
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Departments", description = "Per-company departments and the roles they offer")
public class DepartmentController {

    private final DepartmentService service;
    private final DepartmentMapper mapper;

    @PostMapping
    @Operation(summary = "Create a department",
            description = """
                    Creates a department inside the caller's own company. `COMPANY_ADMIN` only.

                    The code is uppercased and spaces become underscores. Code and name must
                    be unique within the company, including against soft-deleted departments.
                    `roleIds` may be empty; the new department starts `ACTIVE`.
                    """)
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
            @Valid @RequestBody CreateDepartmentRequest request) {

        Department department = service.create(mapper.toCommand(request));
        List<CompanyRole> roles = service.rolesOf(department.getId());

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/departments/{id}")
                        .buildAndExpand(department.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(department, roles), "Department created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a department",
            description = """
                    Full replacement of the editable fields, including the role grants —
                    `roleIds` is the complete set the department should hold afterwards, not
                    a delta. `COMPANY_ADMIN` only.

                    `version` is required and must match the version last read, or the
                    request is rejected with `409 CONCURRENT_MODIFICATION`. `departmentCode`
                    is immutable.
                    """)
    public ApiResponse<DepartmentResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateDepartmentRequest request) {
        Department department = service.update(id, mapper.toCommand(request));
        return ApiResponse.success(
                mapper.toResponse(department, service.rolesOf(id)), "Department updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a department",
            description = "A `COMPANY_ADMIN` sees only their own company's departments — "
                    + "another company's id returns 404, not 403. A `SUPER_ADMIN` may read any.")
    public ApiResponse<DepartmentResponse> get(@PathVariable UUID id) {
        Department department = service.getById(id);
        return ApiResponse.success(mapper.toResponse(department, service.rolesOf(id)));
    }

    @GetMapping
    @Operation(summary = "List departments",
            description = "Every department of the caller's own company, ordered by name. "
                    + "Unpaged. A `SUPER_ADMIN` with no bound company sees nothing here — use "
                    + "the assignable/per-id endpoints for support lookups.")
    public ApiResponse<List<DepartmentResponse>> list() {
        List<Department> departments = service.listAll();
        Map<UUID, List<CompanyRole>> rolesByDepartment =
                service.rolesOf(departments.stream().map(Department::getId).toList());

        return ApiResponse.success(departments.stream()
                .map(d -> mapper.toResponse(d, rolesByDepartment.getOrDefault(d.getId(), List.of())))
                .toList());
    }

    @GetMapping("/assignable")
    @Operation(summary = "List assignable departments",
            description = "The company's ACTIVE departments and the roles each offers, for "
                    + "the department picker on user creation. Unpaged.")
    public ApiResponse<List<DepartmentResponse>> assignable() {
        List<Department> departments = service.listAssignable();
        Map<UUID, List<CompanyRole>> rolesByDepartment =
                service.rolesOf(departments.stream().map(Department::getId).toList());

        return ApiResponse.success(departments.stream()
                .map(d -> mapper.toResponse(d, rolesByDepartment.getOrDefault(d.getId(), List.of())))
                .toList());
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a department", description = "Returns the department to the assignment list. Idempotent.")
    public ApiResponse<DepartmentResponse> activate(@PathVariable UUID id) {
        Department department = service.activate(id);
        return ApiResponse.success(
                mapper.toResponse(department, service.rolesOf(id)), "Department activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a department",
            description = "Withdraws the department from the assignment list. Users already "
                    + "placed in it, and the roles they hold, are unaffected. Idempotent.")
    public ApiResponse<DepartmentResponse> deactivate(@PathVariable UUID id) {
        Department department = service.deactivate(id);
        return ApiResponse.success(
                mapper.toResponse(department, service.rolesOf(id)), "Department deactivated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a department",
            description = "Soft delete: the row is retained and its code stays reserved. "
                    + "Refused while any user is still placed in this department.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Department deleted"));
    }
}
