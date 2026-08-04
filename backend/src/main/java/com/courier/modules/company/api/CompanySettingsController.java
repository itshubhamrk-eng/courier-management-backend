package com.courier.modules.company.api;

import com.courier.modules.company.api.dto.CompanySettingsRequest;
import com.courier.modules.company.api.dto.CompanySettingsResponse;
import com.courier.modules.company.application.CompanySettingsService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A company's configurable behaviour settings.
 *
 * <p>Always operates on the caller's own company — there is one settings row per company,
 * taken from the company in the JWT, so no id is in the path. Any authenticated company
 * user may read (the values drive many modules); only {@code COMPANY_ADMIN} may write.
 *
 * <p>The full {@code PUT} updates every section at once and honours `version` for
 * optimistic locking; each {@code PATCH /section} updates only that section. All writes
 * merge the fields supplied and leave the rest unchanged.
 *
 * <p>This is the wide, admin-tunable settings — distinct from the plan-derived key/value
 * settings shown at {@code GET /api/v1/companies/{id}/settings}.
 */
@RestController
@RequestMapping("/api/v1/company-settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Company Settings", description = "Per-company configurable behaviour (COMPANY_ADMIN writes)")
public class CompanySettingsController {

    private final CompanySettingsService service;
    private final CompanySettingsMapper mapper;

    @GetMapping
    @Operation(summary = "Get the company's settings",
            description = "Returns the caller's company settings, creating the row with "
                    + "defaults on first access. Readable by any authenticated company user.")
    public ApiResponse<CompanySettingsResponse> get() {
        return ApiResponse.success(mapper.toResponse(service.get()));
    }

    @PutMapping
    @Operation(summary = "Replace all settings",
            description = "Updates every section. Supplied fields are applied, omitted ones "
                    + "left unchanged. `version` is required and a stale value returns 409.")
    public ApiResponse<CompanySettingsResponse> replace(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.replace(mapper.toCommand(request))), "Settings updated");
    }

    @PatchMapping("/general")
    @Operation(summary = "Update general & regional settings")
    public ApiResponse<CompanySettingsResponse> general(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.patchGeneral(mapper.toCommand(request))),
                "General settings updated");
    }

    @PatchMapping("/shipment")
    @Operation(summary = "Update shipment settings")
    public ApiResponse<CompanySettingsResponse> shipment(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.patchShipment(mapper.toCommand(request))),
                "Shipment settings updated");
    }

    @PatchMapping("/finance")
    @Operation(summary = "Update finance settings")
    public ApiResponse<CompanySettingsResponse> finance(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.patchFinance(mapper.toCommand(request))),
                "Finance settings updated");
    }

    @PatchMapping("/security")
    @Operation(summary = "Update security settings",
            description = "Stored per company. Note: auth does not yet read these — they "
                    + "become effective when auth is made company-owned.")
    public ApiResponse<CompanySettingsResponse> security(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.patchSecurity(mapper.toCommand(request))),
                "Security settings updated");
    }

    @PatchMapping("/notification")
    @Operation(summary = "Update notification settings")
    public ApiResponse<CompanySettingsResponse> notification(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.patchNotification(mapper.toCommand(request))),
                "Notification settings updated");
    }

    @PatchMapping("/branding")
    @Operation(summary = "Update branding settings")
    public ApiResponse<CompanySettingsResponse> branding(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.patchBranding(mapper.toCommand(request))),
                "Branding settings updated");
    }
}
