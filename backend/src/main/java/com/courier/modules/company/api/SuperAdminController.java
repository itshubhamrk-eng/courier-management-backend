package com.courier.modules.company.api;

import com.courier.modules.auth.application.SuperAdminAccountService;
import com.courier.modules.auth.application.UserProvisioningService;
import com.courier.modules.company.api.dto.CreateSuperAdminRequest;
import com.courier.modules.company.api.dto.PlatformDashboardResponse;
import com.courier.modules.company.api.dto.SuperAdminUserResponse;
import com.courier.modules.company.application.CompanyDashboardService;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The platform console. {@code SUPER_ADMIN} only.
 *
 * <p>Everything here is about the platform rather than any one company, which is what
 * separates it from {@code CompanyController}: that one always operates on a company
 * identified in the path.
 *
 * <p><b>What a super admin cannot reach, deliberately.</b> There is no endpoint here —
 * and none anywhere else that admits a {@code SUPER_ADMIN} — for creating a branch, a
 * shipment, a customer, a manifest, or for recharging a wallet. Those are a company's
 * own operations, performed by its own staff under its own roles. A platform operator
 * booking a shipment "to help" would be indistinguishable in the data from the company
 * having booked it, and every such record would be unattributable afterwards.
 */
@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Super Admin", description = "Platform-wide console (SUPER_ADMIN)")
public class SuperAdminController {

    private final CompanyDashboardService dashboardService;
    private final SuperAdminAccountService accountService;
    private final CompanyMapper mapper;

    @GetMapping("/dashboard")
    @Operation(summary = "Platform dashboard",
            description = """
                    Totals for the platform as a whole: how many companies exist, how
                    they break down by lifecycle state, how many have lapsed, how many
                    lapse within thirty days, and the size of the plan catalogue.

                    `companiesByStatus` always carries every status, including the ones
                    at zero, so a client can render a fixed row of tiles without
                    inventing the keys the query happened not to return.

                    `upcomingRenewals` is the worklist itself — companies whose trial or
                    subscription ends within thirty days, plus those already lapsed,
                    soonest first and capped at twenty. It is a to-do list, not a report.

                    **No shipment figure**, for the same reason company statistics carry
                    none: the module that would produce one does not exist, and a
                    constant zero is a lie told in a number.
                    """)
    public ApiResponse<PlatformDashboardResponse> dashboard() {
        return ApiResponse.success(mapper.toResponse(dashboardService.platformDashboard()));
    }

    @PostMapping("/users")
    @Operation(summary = "Create a super admin",
            description = """
                    Creates another platform operator, holding `SUPER_ADMIN` and nothing
                    else — there is no role field, because a role parameter here would
                    be a way to mint any account on the platform from the endpoint with
                    the least surface.

                    The address must be unused **across the whole platform**, not merely
                    within one company. Ordinary accounts are unique per company because
                    two unrelated businesses may both employ the same person; a platform
                    operator signs in with no company code at all, and the home company
                    is resolved by finding the single platform account with that address.
                    A second one would make that lookup ambiguous — and an ambiguous
                    match is refused as a bad credential, so the newer account could
                    never sign in at all.

                    Omit `password` and one is generated and returned **once** in
                    `temporaryPassword`. It is never logged, audited or retrievable
                    again. The account is created ACTIVE and pre-verified: a platform
                    operator is onboarded by another platform operator, and there is
                    nobody above them to recover the account if an email never arrives.
                    """)
    public ResponseEntity<ApiResponse<SuperAdminUserResponse>> createUser(
            @Valid @RequestBody CreateSuperAdminRequest request) {

        UserProvisioningService.ProvisionedBranchUser created = accountService.create(
                request.email(), request.firstName(), request.lastName(),
                request.phone(), request.password(), request.homeCompanyId());

        SuperAdminUserResponse body = new SuperAdminUserResponse(
                created.userId(),
                request.homeCompanyId(),
                created.email(),
                request.firstName(),
                request.lastName(),
                request.phone(),
                "ACTIVE",
                true,
                List.of(Roles.SUPER_ADMIN),
                null,
                null,
                created.temporaryPassword());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(body, "Super admin created"));
    }

    @GetMapping("/users")
    @Operation(summary = "List platform operators",
            description = """
                    Every account that can act outside a single company — `SUPER_ADMIN`
                    and `PLATFORM_ADMIN` alike.

                    Platform admins are included deliberately: the question this answers
                    is "who operates above the companies", and omitting the role that can
                    impersonate any company would make the answer wrong in the one
                    direction that matters.

                    No password is ever present on this response.
                    """)
    public ApiResponse<List<SuperAdminUserResponse>> listUsers() {
        return ApiResponse.success(accountService.list().stream()
                .map(user -> new SuperAdminUserResponse(
                        user.getId(),
                        user.getCompanyId(),
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getPhone(),
                        user.getStatus().name(),
                        user.isEmailVerified(),
                        user.getRoles().stream().map(Enum::name).sorted().toList(),
                        user.getLastLoginAt(),
                        user.getCreatedAt(),
                        // Never on a read. The one copy was in the create response.
                        null))
                .toList());
    }
}
