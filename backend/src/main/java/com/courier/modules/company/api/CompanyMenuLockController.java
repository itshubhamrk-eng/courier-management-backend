package com.courier.modules.company.api;

import com.courier.modules.company.application.CompanyMenuLockService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sidebar menus a SUPER_ADMIN hides from a company. The write side lives under
 * {@code /companies/**} (URL-gated to SUPER_ADMIN in SecurityConfig, and re-checked in the
 * service); {@code /menu-locks/me} is what the signed-in user's own sidebar reads.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Company Menu Locks", description = "Menus a super admin hides from a company")
public class CompanyMenuLockController {

    private final CompanyMenuLockService service;

    public record MenuLocksRequest(List<String> lockedMenus) { }

    public record MenuLocksResponse(List<String> lockedMenus) { }

    @GetMapping("/companies/{companyId}/menu-locks")
    @Operation(summary = "Menus hidden from a company", description = "SUPER_ADMIN only.")
    public ApiResponse<MenuLocksResponse> get(@PathVariable UUID companyId) {
        return ApiResponse.success(new MenuLocksResponse(service.lockedMenus(companyId)));
    }

    @PutMapping("/companies/{companyId}/menu-locks")
    @Operation(summary = "Replace the menus hidden from a company", description = "SUPER_ADMIN only.")
    public ApiResponse<MenuLocksResponse> replace(@PathVariable UUID companyId,
                                                  @RequestBody MenuLocksRequest request) {
        return ApiResponse.success(
                new MenuLocksResponse(service.replaceLockedMenus(companyId, request.lockedMenus())),
                "Menu access updated");
    }

    @GetMapping("/menu-locks/me")
    @Operation(summary = "Menus hidden from the caller's company",
            description = "Empty for a tenant-unbound user (super admin).")
    public ApiResponse<MenuLocksResponse> mine() {
        return ApiResponse.success(new MenuLocksResponse(service.lockedMenusOfCurrentCompany()));
    }
}
