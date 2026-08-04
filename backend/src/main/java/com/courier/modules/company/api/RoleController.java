package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.CreateRoleRequest;
import com.courier.modules.company.api.dto.RoleResponse;
import com.courier.modules.company.api.dto.RoleSearchRequest;
import com.courier.modules.company.api.dto.RoleSummaryResponse;
import com.courier.modules.company.api.dto.UpdateRoleRequest;
import com.courier.modules.company.application.RolePermissionService;
import com.courier.modules.company.application.RoleService;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Roles within a company.
 *
 * <p><b>Who may do what</b>, enforced on {@code RoleService}, not here:
 * {@code COMPANY_ADMIN} manages the roles of their own company; {@code SUPER_ADMIN} may
 * read across every company but never write. The company is always taken from the
 * verified JWT, never from a request body or query parameter.
 *
 * <p>Permission management is a separate module: a role's permissions are returned but
 * cannot be set here, and a newly created role starts with none.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Roles", description = "Per-company roles (COMPANY_ADMIN writes, SUPER_ADMIN reads)")
public class RoleController {

    /**
     * Sortable properties, whitelisted.
     *
     * <p>Spring binds {@code ?sort=} straight onto an entity attribute name: an unknown
     * name throws {@code PropertyReferenceException} deep in the repository and can only
     * be rendered as a 500. The map also hides the {@code isDefault} → {@code defaultRole}
     * and {@code createdDate} → {@code createdAt} field-name differences from clients.
     */
    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("roleCode", "roleCode"),
            Map.entry("roleName", "roleName"),
            Map.entry("roleType", "roleType"),
            Map.entry("status", "status"),
            Map.entry("isSystemRole", "systemRole"),
            Map.entry("systemRole", "systemRole"),
            Map.entry("isDefault", "defaultRole"),
            Map.entry("defaultRole", "defaultRole"),
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedDate", "updatedAt"));

    private static final int MAX_PAGE_SIZE = 100;

    private final RoleService service;
    private final RolePermissionService rolePermissionService;
    private final RoleMapper mapper;

    @PostMapping
    @Operation(summary = "Create a role",
            description = """
                    Creates a role inside the caller's own company. `COMPANY_ADMIN` only.

                    The code is uppercased and spaces become underscores. Code and name
                    must be unique within the company, **including against soft-deleted
                    roles** — a deleted role keeps its code reserved.

                    The new role starts `ACTIVE` with **no permissions**: granting them
                    belongs to the Permission module. Setting `isDefault` demotes
                    whichever role currently holds that flag.
                    """)
    public ResponseEntity<ApiResponse<RoleResponse>> create(
            @Valid @RequestBody CreateRoleRequest request) {

        CompanyRole role = service.create(mapper.toCommand(request));

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/roles/{id}")
                        .buildAndExpand(role.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(role), "Role created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a role",
            description = """
                    Full replacement of the editable fields. `COMPANY_ADMIN` only.

                    `version` is required and must match the version last read, or the
                    request is rejected with `409 CONCURRENT_MODIFICATION`. `roleCode` is
                    immutable, and `status` moves through the activate/deactivate
                    endpoints.

                    A **system role may be renamed and re-typed** — calling your admins
                    "Owners" is legitimate — but never deleted.
                    """)
    public ApiResponse<RoleResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateRoleRequest request) {
        CompanyRole role = service.update(id, mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(role, permissionCodes(id)), "Role updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a role",
            description = "A `COMPANY_ADMIN` sees only their own company's roles — another "
                    + "company's id returns 404, not 403, so the response cannot be used to "
                    + "probe what exists elsewhere. A `SUPER_ADMIN` may read any role.")
    public ApiResponse<RoleResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id), permissionCodes(id)));
    }

    @GetMapping
    @Operation(summary = "List roles",
            description = """
                    Paged, sorted, filtered and searchable. Filters combine with AND.

                    For a `COMPANY_ADMIN` the result is always their own company's roles —
                    a `companyId` in the query string is **overridden**, not honoured, so
                    supplying someone else's returns your own roles rather than theirs. A
                    `SUPER_ADMIN` sees every company's roles and can pin the listing to
                    one with `companyId`.

                    `permission` finds every role granting a specific right — the query
                    to run before changing what a permission means.

                    Sort accepts `roleCode`, `roleName`, `roleType`, `status`,
                    `isSystemRole`, `isDefault`, `createdDate`, `updatedDate`; anything
                    else is rejected with 400. `size` is capped at 100.
                    """)
    public ApiResponse<PageResponse<RoleSummaryResponse>> list(
            @Valid @ParameterObject RoleSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "roleCode") Pageable pageable) {

        Page<CompanyRole> page = service.search(mapper.toCriteria(search), sanitise(pageable));

        // One batched count for the whole page: a per-row lookup would be N+1, and a
        // super admin's page spans every company.
        Map<UUID, Integer> counts = rolePermissionService.countByRoles(
                page.getContent().stream().map(CompanyRole::getId).toList());

        return ApiResponse.success(PageResponse.from(page,
                role -> mapper.toSummary(role, counts.getOrDefault(role.getId(), 0))));
    }

    @GetMapping("/assignable")
    @Operation(summary = "List assignable roles",
            description = "The company's ACTIVE roles, for a role picker. Unpaged — a "
                    + "company has a handful of roles, not a catalogue.")
    public ApiResponse<List<RoleSummaryResponse>> assignable() {
        return ApiResponse.success(service.listAssignable().stream()
                .map(mapper::toSummary)
                .toList());
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a role",
            description = "Returns the role to the assignment list. Idempotent.")
    public ApiResponse<RoleResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(
                mapper.toResponse(service.activate(id), permissionCodes(id)), "Role activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a role",
            description = """
                    Withdraws the role from the assignment list. **Users who already hold
                    it keep it** — a deactivation that stripped access from everyone would
                    be an outage, not a configuration change.

                    Refused for the company's default role: new users would otherwise be
                    created with a role nobody may hold. Idempotent.
                    """)
    public ApiResponse<RoleResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(
                mapper.toResponse(service.deactivate(id), permissionCodes(id)), "Role deactivated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a role",
            description = """
                    Soft delete: the row is retained and its code stays reserved.

                    Refused for a **system role** (deactivate it instead) and for the
                    company's **default role**. Users currently holding the role are not
                    reassigned — that belongs to User Management.
                    """)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        // 200 rather than 204: every response carries the standard envelope, and a 204
        // must have an empty body.
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Role deleted"));
    }

    private List<String> permissionCodes(UUID roleId) {
        return rolePermissionService.resolveEffectiveCodes(List.of(roleId));
    }

    /**
     * Caps the page size and rejects sort properties outside {@link #SORTABLE}. An
     * uncapped {@code size} turns one request into a full table scan — and for a super
     * admin, across every company at once.
     */
    private Pageable sanitise(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String property = SORTABLE.get(order.getProperty());
                    if (property == null) {
                        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                                "Cannot sort by '%s'. Allowed: %s"
                                        .formatted(order.getProperty(), sortableNames()));
                    }
                    return new Sort.Order(order.getDirection(), property);
                })
                .toList();

        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.asc("roleCode")) : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }

    private static String sortableNames() {
        // The entity's own field names are accepted but not advertised; only the
        // documented client spellings are.
        Set<String> names = new TreeSet<>(SORTABLE.keySet());
        names.removeAll(Set.of("systemRole", "defaultRole", "createdAt"));
        return String.join(", ", names);
    }
}
