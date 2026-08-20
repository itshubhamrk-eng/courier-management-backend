package com.courier.modules.finance.api;

import com.courier.modules.finance.api.dto.CompanyRazorpayConfigRequest;
import com.courier.modules.finance.api.dto.CompanyRazorpayConfigResponse;
import com.courier.modules.finance.application.CompanyRazorpayConfigService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A company's own Razorpay account, used for wallet recharge instead of the platform-wide
 * gateway once configured. Always operates on the caller's own company — no id in the
 * path, same reasoning as {@code CompanySettingsController}.
 *
 * <p>{@code COMPANY_ADMIN} only, for both read and write — narrower than company settings,
 * because even a masked view of payment credentials is more sensitive than the rest.
 */
@RestController
@RequestMapping("/api/v1/company-razorpay-config")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Company Razorpay Config", description = "Per-company Razorpay credentials (COMPANY_ADMIN only)")
public class CompanyRazorpayConfigController {

    private final CompanyRazorpayConfigService service;
    private final CompanyRazorpayConfigMapper mapper;

    @GetMapping
    @Operation(summary = "Get the company's Razorpay config",
            description = "Never returns the key secret — only whether one is set. "
                    + "`COMPANY_ADMIN` only.")
    public ApiResponse<CompanyRazorpayConfigResponse> get() {
        return ApiResponse.success(mapper.toResponse(service.get()));
    }

    @PutMapping
    @Operation(summary = "Set the company's Razorpay credentials",
            description = "A blank `keySecret` keeps the one already stored. Enabling "
                    + "requires a key id and a secret (new or already stored).")
    public ApiResponse<CompanyRazorpayConfigResponse> update(
            @Valid @RequestBody CompanyRazorpayConfigRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.update(mapper.toCommand(request))),
                "Razorpay configuration updated");
    }
}
