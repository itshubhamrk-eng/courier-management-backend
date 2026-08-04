package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.CreatePermissionRequest;
import com.courier.modules.company.api.dto.PermissionResponse;
import com.courier.modules.company.api.dto.PermissionSearchRequest;
import com.courier.modules.company.api.dto.UpdatePermissionRequest;
import com.courier.modules.company.application.PermissionService;
import com.courier.modules.company.domain.Permission;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The permission catalogue — the platform's authorisation vocabulary.
 *
 * <p>{@code SUPER_ADMIN} writes; {@code COMPANY_ADMIN} reads, because building a role
 * means seeing what can be granted. Enforced on {@code PermissionService}.
 *
 * <p>Granting permissions to a role lives at {@code /api/v1/roles/{roleId}/permissions}.
 */
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Permissions", description = "Platform permission catalogue (SUPER_ADMIN writes)")
public class PermissionController {

    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("permissionCode", "permissionCode"),
            Map.entry("permissionName", "permissionName"),
            Map.entry("module", "module"),
            Map.entry("action", "action"),
            Map.entry("resource", "resource"),
            Map.entry("status", "status"),
            Map.entry("displayOrder", "displayOrder"),
            Map.entry("isSystemPermission", "systemPermission"),
            Map.entry("systemPermission", "systemPermission"),
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedDate", "updatedAt"));

    private static final int MAX_PAGE_SIZE = 200;

    private final PermissionService service;
    private final PermissionMapper mapper;

    @PostMapping
    @Operation(summary = "Create a permission",
            description = """
                    `SUPER_ADMIN` only. The code is **derived** as `MODULE_ACTION`, so it
                    cannot disagree with the module and action it describes, and the pair
                    must be unique.

                    The result is a custom permission: `isSystemPermission` is false and
                    cannot be requested. Only the seeding migration creates system
                    permissions.
                    """)
    public ResponseEntity<ApiResponse<PermissionResponse>> create(
            @Valid @RequestBody CreatePermissionRequest request) {

        Permission permission = service.create(mapper.toCommand(request));

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/permissions/{id}")
                        .buildAndExpand(permission.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(permission), "Permission created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a permission",
            description = """
                    `SUPER_ADMIN` only, and **refused for a system permission** — their
                    codes appear in `@PreAuthorize` expressions and in every company's
                    grants, so they are read-only.

                    Module and action cannot change: together they are the code. `version`
                    is required and a stale value returns 409.
                    """)
    public ApiResponse<PermissionResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdatePermissionRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Permission updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a permission")
    public ApiResponse<PermissionResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List permissions",
            description = """
                    Paged, sorted, filtered and searchable. Filters combine with AND.

                    `module` and `action` are repeatable. `planGatedOnly=true` returns the
                    permissions that depend on a subscription feature — the ones a company
                    may be refused when granting.

                    Sort accepts `permissionCode`, `permissionName`, `module`, `action`,
                    `resource`, `status`, `displayOrder`, `isSystemPermission`,
                    `createdDate`, `updatedDate`. `size` is capped at 200, since a
                    permission matrix legitimately fetches a whole module at once.
                    """)
    public ApiResponse<PageResponse<PermissionResponse>> list(
            @Valid @ParameterObject PermissionSearchRequest search,
            @ParameterObject @PageableDefault(size = 50, sort = "displayOrder") Pageable pageable) {

        Page<Permission> page = service.search(mapper.toCriteria(search), sanitise(pageable));
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @GetMapping("/grantable")
    @Operation(summary = "List every grantable permission",
            description = "All ACTIVE permissions in display order, unpaged — the source "
                    + "for a permission matrix screen. Plan-gated entries are included; "
                    + "whether a given company may hold one is decided when granting.")
    public ApiResponse<List<PermissionResponse>> grantable() {
        return ApiResponse.success(service.listGrantable().stream()
                .map(mapper::toResponse)
                .toList());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a permission",
            description = """
                    Soft delete. `SUPER_ADMIN` only.

                    Refused for a **system permission**, and refused while **any role in
                    any company still holds it** — deleting would strip access with no
                    warning and no record of what was lost. Deactivate instead: existing
                    grants keep working while the right is withdrawn from the catalogue.
                    """)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        // 200 rather than 204: every response carries the standard envelope.
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Permission deleted"));
    }

    /** Caps the page size and rejects sort properties outside the whitelist. */
    private Pageable sanitise(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String property = SORTABLE.get(order.getProperty());
                    if (property == null) {
                        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                                "Cannot sort by '%s'. Allowed: %s"
                                        .formatted(order.getProperty(),
                                                String.join(", ", new TreeSet<>(SORTABLE.keySet()))));
                    }
                    return new Sort.Order(order.getDirection(), property);
                })
                .toList();

        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.asc("displayOrder")) : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
