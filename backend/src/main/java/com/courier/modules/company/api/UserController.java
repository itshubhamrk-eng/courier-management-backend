package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.AssignPlacementRequest;
import com.courier.modules.company.api.dto.AssignRoleRequest;
import com.courier.modules.company.api.dto.ChangePasswordRequest;
import com.courier.modules.company.api.dto.CreateUserRequest;
import com.courier.modules.company.api.dto.LockUserRequest;
import com.courier.modules.company.api.dto.ResetPasswordRequest;
import com.courier.modules.company.api.dto.UpdateUserRequest;
import com.courier.modules.company.api.dto.UserResponse;
import com.courier.modules.company.api.dto.UserSearchRequest;
import com.courier.modules.company.api.dto.UserSummaryResponse;
import com.courier.modules.company.application.UserService;
import com.courier.modules.company.domain.User;
import com.courier.modules.company.domain.UserRole;
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
import java.util.TreeSet;
import java.util.UUID;

/**
 * Company users.
 *
 * <p>Enforced on {@code UserService}: {@code COMPANY_ADMIN} writes within their company;
 * {@code SUPER_ADMIN} reads across all; {@code BRANCH_MANAGER} / {@code HUB_MANAGER} read
 * only their own branch / hub. The company is always the caller's, taken from the JWT.
 *
 * <p>Users inherit permissions through the company roles assigned here; the Branch, Hub,
 * Customer and Shipment modules are separate and not reachable from this controller.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Company user administration")
public class UserController {

    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("employeeCode", "employeeCode"),
            Map.entry("firstName", "firstName"),
            Map.entry("lastName", "lastName"),
            Map.entry("displayName", "displayName"),
            Map.entry("email", "email"),
            Map.entry("username", "username"),
            Map.entry("designation", "designation"),
            Map.entry("department", "department"),
            Map.entry("status", "status"),
            Map.entry("joiningDate", "joiningDate"),
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedDate", "updatedAt"));

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService service;
    private final UserMapper mapper;

    @PostMapping
    @Operation(summary = "Create a user",
            description = """
                    `COMPANY_ADMIN` only. Email is unique per company; username is
                    globally unique; employee code is unique per company — all checked
                    against soft-deleted rows too.

                    Omit `password` to create a PENDING account with an unusable password
                    that an admin resets. Omit `roleIds` to apply the company's default
                    role. The initial password, if generated, is never returned.
                    """)
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request) {

        UserService.CreatedUser created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/users/{id}")
                        .buildAndExpand(created.user().getId()).toUri())
                .body(ApiResponse.success(mapper.toCreatedResponse(created), "User created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user",
            description = "Full replacement of the profile. `version` required; a stale "
                    + "value returns 409. Email, username and employee code are immutable "
                    + "here; status, lock and password have their own endpoints.")
    public ApiResponse<UserResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateUserRequest request) {
        User user = service.update(id, mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(user, roleCodes(id)), "User updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a user",
            description = "A branch/hub manager sees only users at their own placement; a "
                    + "foreign or out-of-scope id returns 404, not 403.")
    public ApiResponse<UserResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id), roleCodes(id)));
    }

    @GetMapping
    @Operation(summary = "List users",
            description = """
                    Paged, sorted, filtered, searchable. `COMPANY_ADMIN` and `SUPER_ADMIN`
                    see the whole company (or, for super admin, all companies); a branch or
                    hub manager sees only their own placement regardless of the filters.

                    Sort: `employeeCode`, `firstName`, `lastName`, `displayName`, `email`,
                    `username`, `designation`, `department`, `status`, `joiningDate`,
                    `createdDate`, `updatedDate`. `size` capped at 100.
                    """)
    public ApiResponse<PageResponse<UserSummaryResponse>> list(
            @Valid @ParameterObject UserSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        Page<User> page = service.search(mapper.toCriteria(search), sanitise(pageable));
        // One batched count for the whole page, not a query per row.
        Map<UUID, Integer> counts = service.roleCounts(
                page.getContent().stream().map(User::getId).toList());
        return ApiResponse.success(PageResponse.from(page,
                user -> mapper.toSummary(user, counts.getOrDefault(user.getId(), 0))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user",
            description = "Soft delete. The account disappears from every read and can no "
                    + "longer authenticate; the email, username and employee code stay "
                    + "reserved.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("User deleted"));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a user", description = "Idempotent.")
    public ApiResponse<UserResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id), roleCodes(id)),
                "User activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a user",
            description = "Sets the account to DISABLED — it can no longer log in. Idempotent. "
                    + "You cannot deactivate your own account.")
    public ApiResponse<UserResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id), roleCodes(id)),
                "User deactivated");
    }

    @PatchMapping("/{id}/lock")
    @Operation(summary = "Lock a user",
            description = "Hard admin lock — cleared only by unlock, never by a scheduled "
                    + "job. Distinct from the automatic failed-login lock. You cannot lock "
                    + "your own account.")
    public ApiResponse<UserResponse> lock(@PathVariable UUID id,
                                          @Valid @RequestBody LockUserRequest request) {
        return ApiResponse.success(mapper.toResponse(service.lock(id, request.reason()), roleCodes(id)),
                "User locked");
    }

    @PatchMapping("/{id}/unlock")
    @Operation(summary = "Unlock a user",
            description = "Clears the admin lock and resets the failed-login counter. Idempotent.")
    public ApiResponse<UserResponse> unlock(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.unlock(id), roleCodes(id)), "User unlocked");
    }

    @PatchMapping("/{id}/reset-password")
    @Operation(summary = "Reset a user's password",
            description = "Admin action — no current password needed. Subject to the "
                    + "password policy. Re-enables a PENDING account.")
    public ApiResponse<Void> resetPassword(@PathVariable UUID id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request.newPassword(), request.mustChange());
        return ApiResponse.success("Password reset");
    }

    @PatchMapping("/{id}/change-password")
    @Operation(summary = "Change your own password",
            description = "Self-service: requires the current password. A user may only "
                    + "change their own password; anyone else's requires reset-password.")
    public ApiResponse<Void> changePassword(@PathVariable UUID id,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(id, request.currentPassword(), request.newPassword());
        return ApiResponse.success("Password changed");
    }

    @PostMapping("/{id}/roles")
    @Operation(summary = "Assign a role to a user",
            description = "Adds a company role. Idempotent — a role the user already holds "
                    + "is left untouched. Returns the user's full role list.")
    public ApiResponse<List<String>> assignRole(@PathVariable UUID id,
                                                @Valid @RequestBody AssignRoleRequest request) {
        List<UserRole> roles = service.assignRole(id, request.roleId());
        return ApiResponse.success(roles.stream().map(UserRole::getRoleCode).sorted().toList(),
                "Role assigned");
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @Operation(summary = "Remove a role from a user", description = "Idempotent.")
    public ResponseEntity<ApiResponse<Void>> removeRole(@PathVariable UUID id,
                                                        @PathVariable UUID roleId) {
        service.removeRole(id, roleId);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Role removed"));
    }

    @PatchMapping("/{id}/branch")
    @Operation(summary = "Assign or clear the user's branch",
            description = "A user belongs to at most one branch. A null id clears it.")
    public ApiResponse<UserResponse> assignBranch(@PathVariable UUID id,
                                                  @RequestBody AssignPlacementRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.assignBranch(id, request.id()), roleCodes(id)),
                "Branch assigned");
    }

    @PatchMapping("/{id}/hub")
    @Operation(summary = "Assign or clear the user's hub",
            description = "A user belongs to at most one hub. A null id clears it.")
    public ApiResponse<UserResponse> assignHub(@PathVariable UUID id,
                                               @RequestBody AssignPlacementRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.assignHub(id, request.id()), roleCodes(id)),
                "Hub assigned");
    }

    // -------------------------------------------------------------------- helpers

    private List<String> roleCodes(UUID userId) {
        return service.listRoles(userId).stream().map(UserRole::getRoleCode).sorted().toList();
    }

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
        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.desc("createdAt")) : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
