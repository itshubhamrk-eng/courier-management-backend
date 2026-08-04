package com.courier.modules.finance.api;

import com.courier.modules.finance.api.dto.CreateTopupRequestRequest;
import com.courier.modules.finance.api.dto.DecideTopupRequestRequest;
import com.courier.modules.finance.api.dto.TopupRequestResponse;
import com.courier.modules.finance.api.dto.TopupRequestSearchRequest;
import com.courier.modules.finance.application.WalletTopupRequestService;
import com.courier.modules.finance.domain.WalletTopupRequest;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * A branch's ask to fund its own wallet, and a company admin's decision on it.
 *
 * <p>Distinct from {@code /branch-wallet/recharge}, which is the branch paying the platform
 * directly: this is the branch asking its own company to fund the wallet, with the money
 * moving only once a {@code COMPANY_ADMIN} approves — see {@code WalletTopupRequestService}.
 */
@RestController
@RequestMapping("/api/v1/branch-wallet/topup-requests")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Wallet Top-up Requests", description = "Branch asks, company admin approves or rejects")
public class TopupRequestController {

    private final WalletTopupRequestService service;
    private final TopupRequestMapper mapper;

    @PostMapping
    @Operation(summary = "Raise a top-up request",
            description = "COMPANY_ADMIN or BRANCH_MANAGER. A branch caller's own branch is "
                    + "used regardless of what is passed; a company admin must name one. "
                    + "Nothing is credited yet.")
    public ResponseEntity<ApiResponse<TopupRequestResponse>> create(
            @Valid @RequestBody CreateTopupRequestRequest request) {
        WalletTopupRequest created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/branch-wallet/topup-requests/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Top-up request raised"));
    }

    @GetMapping("/{id}")
    public ApiResponse<TopupRequestResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List top-up requests",
            description = "A branch caller (BRANCH_MANAGER) sees only their own branch's "
                    + "requests regardless of the `branchId` filter. A COMPANY_ADMIN sees "
                    + "the whole company and may narrow to one branch.")
    public ApiResponse<PageResponse<TopupRequestResponse>> list(
            @ParameterObject TopupRequestSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<WalletTopupRequest> page = service.search(mapper.toCriteria(search), pageable);
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approve a top-up request",
            description = "COMPANY_ADMIN only. Credits the branch wallet for the requested "
                    + "amount (a manual credit, same as `POST /branch-wallet/credit`) and "
                    + "marks the request APPROVED, in one transaction. Refused if the "
                    + "request is not PENDING.")
    public ApiResponse<TopupRequestResponse> approve(@PathVariable UUID id,
                                                      @Valid @RequestBody(required = false) DecideTopupRequestRequest request) {
        String remarks = request == null ? null : request.remarks();
        return ApiResponse.success(mapper.toResponse(service.approve(id, remarks)), "Top-up request approved");
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject a top-up request",
            description = "COMPANY_ADMIN only. Marks the request REJECTED. Moves nothing.")
    public ApiResponse<TopupRequestResponse> reject(@PathVariable UUID id,
                                                     @Valid @RequestBody(required = false) DecideTopupRequestRequest request) {
        String remarks = request == null ? null : request.remarks();
        return ApiResponse.success(mapper.toResponse(service.reject(id, remarks)), "Top-up request rejected");
    }
}
