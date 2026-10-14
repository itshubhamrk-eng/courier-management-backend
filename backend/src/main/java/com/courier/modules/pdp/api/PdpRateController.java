package com.courier.modules.pdp.api;

import com.courier.modules.pdp.api.PdpRateDtos.CalculateRequest;
import com.courier.modules.pdp.api.PdpRateDtos.CalculateResponse;
import com.courier.modules.pdp.api.PdpRateDtos.RateResponse;
import com.courier.modules.pdp.api.PdpRateDtos.SaveRequest;
import com.courier.modules.pdp.api.PdpRateDtos.SlabResponse;
import com.courier.modules.pdp.application.PdpRateService;
import com.courier.modules.pdp.application.PdpRateService.Quote;
import com.courier.modules.pdp.application.PdpRateService.QuoteCommand;
import com.courier.modules.pdp.application.PdpRateService.SaveCommand;
import com.courier.modules.pdp.application.PdpRateService.SlabInput;
import com.courier.modules.pdp.application.PdpRateView;
import com.courier.modules.pdp.domain.PdpRateType;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

/** Pickup / delivery rate cards of Pickup-Delivery Partners. Writes are {@code COMPANY_ADMIN} (enforced on the service). */
@RestController
@RequestMapping("/api/v1/pdp-rates")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Partner Rates", description = "Pickup and delivery rate cards of Pickup-Delivery Partners")
public class PdpRateController {

    private final PdpRateService service;

    @GetMapping
    @Operation(summary = "List partner rate cards", description = "Optionally for one partner and/or one type.")
    public ApiResponse<List<RateResponse>> list(@RequestParam(required = false) UUID branchId,
                                                @RequestParam(required = false) PdpRateType rateType) {
        return ApiResponse.success(service.search(branchId, rateType).stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a rate card")
    public ApiResponse<RateResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.get(id)));
    }

    @PostMapping
    @Operation(summary = "Create a rate card", description = "`branchId` must be a Pickup-Delivery Partner. Slabs "
            + "are half-open [min,max), non-overlapping; only the last may have no maximum. One active card per "
            + "partner, type and district in any period.")
    public ApiResponse<RateResponse> create(@Valid @RequestBody SaveRequest r) {
        return ApiResponse.success(toResponse(service.create(toCommand(r))), "Rate created");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a rate card (header and slabs)")
    public ApiResponse<RateResponse> update(@PathVariable UUID id, @Valid @RequestBody SaveRequest r) {
        return ApiResponse.success(toResponse(service.update(id, toCommand(r))), "Rate saved");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a rate card")
    public ApiResponse<RateResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.activate(id)), "Rate activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a rate card")
    public ApiResponse<RateResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(toResponse(service.deactivate(id)), "Rate deactivated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a rate card")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Rate deleted");
    }

    @PostMapping("/calculate")
    @Operation(summary = "What a partner is paid for one job", description = "Read-only. District card first, "
            + "else the partner's all-districts card; errors if neither is live on the date.")
    public ApiResponse<CalculateResponse> calculate(@Valid @RequestBody CalculateRequest r) {
        Quote q = service.calculate(new QuoteCommand(r.branchId(), r.rateType(), r.districtId(),
                r.actualWeight(), r.odaApplicable(), r.date()));
        return ApiResponse.success(new CalculateResponse(q.rateId(), q.rateType(), q.actualWeight(),
                q.chargeableWeight(), q.ratePerKg(), q.weightCharge(), q.mathadiCharge(), q.odaCharge(),
                q.total()), "Rate calculated");
    }

    private static SaveCommand toCommand(SaveRequest r) {
        List<SlabInput> slabs = r.slabs().stream()
                .map(s -> new SlabInput(s.minWeight(), s.maxWeight(), s.ratePerKg())).toList();
        return new SaveCommand(r.branchId(), r.rateType(), r.districtId(), r.minChargeableWeight(),
                r.mathadiCharge(), r.odaCharge(), r.failedAttemptFee(), r.returnFee(), r.effectiveFrom(),
                r.effectiveTo(), slabs, r.version());
    }

    private RateResponse toResponse(PdpRateView v) {
        var r = v.rate();
        return new RateResponse(r.getId(), r.getBranchId(), v.branchCode(), v.branchName(), r.getRateType(),
                r.getDistrictId(), r.getMinChargeableWeight(), r.getMathadiCharge(), r.getOdaCharge(),
                r.getFailedAttemptFee(), r.getReturnFee(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getStatus(),
                v.slabs().stream().map(s -> new SlabResponse(s.getId(), s.getMinWeight(), s.getMaxWeight(),
                        s.getRatePerKg())).toList(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getVersion());
    }
}
