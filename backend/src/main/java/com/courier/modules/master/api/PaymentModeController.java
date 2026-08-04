package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreatePaymentModeRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.PaymentModeResponse;
import com.courier.modules.master.api.dto.UpdatePaymentModeRequest;
import com.courier.modules.master.application.PaymentModeService;
import com.courier.modules.master.domain.PaymentMode;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Payment modes — PAID, TO_PAY, TBB, COD.
 *
 * <p>The four canonical modes are rows, not an enum. What the shipment and wallet modules
 * branch on is the behaviour flags, so contradictory combinations — collecting at both
 * ends, or a billed mode that also takes cash — are refused with 422.
 */
@RestController
@RequestMapping("/api/v1/master/payment-modes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Master Data - Payment Modes", description = "Payment mode master")
public class PaymentModeController {

    private final PaymentModeService service;
    private final PaymentModeMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a payment mode",
            description = "`COMPANY_ADMIN` only. Code and name are unique within the company. Contradictory flag combinations are refused with 422.")
    public ResponseEntity<ApiResponse<PaymentModeResponse>> create(
            @Valid @RequestBody CreatePaymentModeRequest request) {
        PaymentMode created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/payment-modes/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Payment mode created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a payment mode",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<PaymentModeResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdatePaymentModeRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Payment mode updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a payment mode", description = "Any authenticated company user.")
    public ApiResponse<PaymentModeResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List payment modes",
            description = """
                    Paged, sorted, filtered, searchable. Filter by `cashOnDelivery`. Sort:
                    `code`, `name`, `status`, `displayOrder`, `createdDate`, `updatedDate`.
                    """)
    public ApiResponse<PageResponse<PaymentModeResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only cash-on-delivery modes")
            @RequestParam(required = false) Boolean cashOnDelivery,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("cashOnDelivery", cashOnDelivery);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a payment mode",
            description = "Soft delete, `COMPANY_ADMIN` only. The code stays reserved.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Payment mode deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a payment mode",
            description = "`COMPANY_ADMIN`. Idempotent.")
    public ApiResponse<PaymentModeResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Payment mode activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a payment mode",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<PaymentModeResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Payment mode deactivated");
    }
}
