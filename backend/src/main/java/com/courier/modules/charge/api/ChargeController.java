package com.courier.modules.charge.api;

import com.courier.modules.charge.api.dto.ChargeResponse;
import com.courier.modules.charge.api.dto.ChargeSearchRequest;
import com.courier.modules.charge.api.dto.ChargeSummaryResponse;
import com.courier.modules.charge.api.dto.CreateChargeRequest;
import com.courier.modules.charge.api.dto.UpdateChargeRequest;
import com.courier.modules.charge.application.ChargeService;
import com.courier.modules.charge.application.ChargeSettingService;
import com.courier.modules.charge.domain.Charge;
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
 * Charges: a named pricing/commission configuration, scoped to one Service Type.
 * {@code COMPANY_ADMIN} only, both reads and writes — see {@link ChargeService}.
 *
 * <p>Configuration only. Nothing here is consumed by Shipment Booking, freight,
 * commission or wallet calculations yet; that integration is separate, later work.
 */
@RestController
@RequestMapping("/api/v1/charges")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Charges", description = "Charge & Charge Settings configuration (not yet wired into booking)")
public class ChargeController {

    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("chargeName", "chargeName"),
            Map.entry("status", "status"),
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedDate", "updatedAt"));

    private static final int MAX_PAGE_SIZE = 100;

    private final ChargeService chargeService;
    private final ChargeSettingService chargeSettingService;
    private final ChargeMapper mapper;

    @PostMapping
    @Operation(summary = "Create a charge", description = "`COMPANY_ADMIN`. A new charge "
            + "always starts ACTIVE. The charge name must be unique within the company and "
            + "service type.")
    public ResponseEntity<ApiResponse<ChargeResponse>> create(
            @Valid @RequestBody CreateChargeRequest request) {
        Charge created = chargeService.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/charges/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(toResponse(created), "Charge created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a charge", description = "`COMPANY_ADMIN`. Full "
            + "replacement of the editable fields. `version` required; a stale value "
            + "returns 409.")
    public ApiResponse<ChargeResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateChargeRequest request) {
        Charge updated = chargeService.update(id, mapper.toCommand(request));
        return ApiResponse.success(toResponse(updated), "Charge updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a charge", description = "`COMPANY_ADMIN`. Includes every "
            + "associated charge setting.")
    public ApiResponse<ChargeResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(chargeService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List charges", description = """
            Paged, sorted, filtered, searchable. Filter by `serviceTypeId`, `status`. Sort:
            `chargeName`, `status`, `createdDate`, `updatedDate`. `size` capped at 100.
            """)
    public ApiResponse<PageResponse<ChargeSummaryResponse>> list(
            @Valid @ParameterObject ChargeSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "chargeName") Pageable pageable) {
        Page<Charge> page = chargeService.search(mapper.toCriteria(search), sanitise(pageable));
        return ApiResponse.success(PageResponse.from(page, mapper::toSummary));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a charge", description = "`COMPANY_ADMIN`. Idempotent.")
    public ApiResponse<ChargeResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(chargeService.activate(id)), "Charge activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a charge", description = "`COMPANY_ADMIN`. Withdraws "
            + "it, and every setting under it, from future use. Idempotent.")
    public ApiResponse<ChargeResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(chargeService.deactivate(id)), "Charge deactivated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a charge", description = "Soft delete, `COMPANY_ADMIN` "
            + "only. Refused (422) while the charge still has any charge setting — remove "
            + "those first.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        chargeService.delete(id);
        return ApiResponse.success("Charge deleted");
    }

    // -------------------------------------------------------------------- helpers

    private ChargeResponse toResponse(Charge charge) {
        List<com.courier.modules.charge.domain.ChargeSetting> settings =
                chargeSettingService.listByCharge(charge.getId());
        return mapper.toResponse(charge, settings);
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
        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.asc("chargeName")) : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
