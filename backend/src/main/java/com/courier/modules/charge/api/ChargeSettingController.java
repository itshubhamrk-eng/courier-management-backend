package com.courier.modules.charge.api;

import com.courier.modules.charge.api.dto.ChargeSettingResponse;
import com.courier.modules.charge.api.dto.CreateChargeSettingRequest;
import com.courier.modules.charge.api.dto.UpdateChargeSettingRequest;
import com.courier.modules.charge.application.ChargeSettingService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.UUID;

/**
 * Charge Settings — the FACTOR/SLAB rows under one Charge. {@code COMPANY_ADMIN} only,
 * both reads and writes — see {@link ChargeSettingService}.
 */
@RestController
@RequestMapping("/api/v1/charges/{chargeId}/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Charge Settings", description = "FACTOR/SLAB rows under one Charge")
public class ChargeSettingController {

    private final ChargeSettingService service;
    private final ChargeSettingMapper mapper;

    @PostMapping
    @Operation(summary = "Add a charge setting", description = "`COMPANY_ADMIN`. Which of "
            + "fromKm/toKm/fromKg/toKg are required depends on chargeType/chargeSlabType. "
            + "For SLAB, refused (422) if it overlaps another active setting of the same "
            + "slab type under this charge.")
    public ResponseEntity<ApiResponse<ChargeSettingResponse>> create(
            @PathVariable UUID chargeId, @Valid @RequestBody CreateChargeSettingRequest request) {
        var created = service.create(mapper.toCommand(chargeId, request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/charges/{chargeId}/settings/{id}")
                        .buildAndExpand(chargeId, created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Charge setting created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a charge setting", description = "`COMPANY_ADMIN`. Full "
            + "replacement. `version` required; a stale value returns 409.")
    public ApiResponse<ChargeSettingResponse> update(@PathVariable UUID chargeId, @PathVariable UUID id,
                                                      @Valid @RequestBody UpdateChargeSettingRequest request) {
        var updated = service.update(id, mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(updated), "Charge setting updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a charge setting")
    public ApiResponse<ChargeSettingResponse> get(@PathVariable UUID chargeId, @PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List a charge's settings", description = "Every setting under "
            + "this charge, in any status — unpaged, since a single charge carries at most "
            + "a handful of bands.")
    public ApiResponse<List<ChargeSettingResponse>> list(@PathVariable UUID chargeId) {
        return ApiResponse.success(service.listByCharge(chargeId).stream().map(mapper::toResponse).toList());
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a charge setting", description = "`COMPANY_ADMIN`. "
            + "Refused (422) if it now overlaps another active setting. Idempotent.")
    public ApiResponse<ChargeSettingResponse> activate(@PathVariable UUID chargeId, @PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Charge setting activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a charge setting", description = "`COMPANY_ADMIN`. Idempotent.")
    public ApiResponse<ChargeSettingResponse> deactivate(@PathVariable UUID chargeId, @PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Charge setting deactivated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a charge setting", description = "Soft delete, `COMPANY_ADMIN` only.")
    public ApiResponse<Void> delete(@PathVariable UUID chargeId, @PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Charge setting deleted");
    }
}
