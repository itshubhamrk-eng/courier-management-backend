package com.courier.modules.rate.api;

import com.courier.modules.rate.api.dto.CreateRateRequest;
import com.courier.modules.rate.api.dto.RateCalculationRequest;
import com.courier.modules.rate.api.dto.RateCalculationResponse;
import com.courier.modules.rate.api.dto.RateResponse;
import com.courier.modules.rate.api.dto.RateSearchRequest;
import com.courier.modules.rate.api.dto.RateSummaryResponse;
import com.courier.modules.rate.api.dto.UpdateRateRequest;
import com.courier.modules.rate.application.RateService;
import com.courier.modules.rate.domain.Rate;
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
 * Rate Master: company rate cards. Enforced on {@link RateService}: {@code COMPANY_ADMIN}
 * creates, updates, activates and deactivates; every authenticated user of the company
 * reads and calculates. The company is always the caller's, from the JWT.
 */
@RestController
@RequestMapping("/api/v1/rates")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Rate Master", description = "Company rate cards and freight calculation")
public class RateController {

    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("rateCode", "rateCode"),
            Map.entry("rateName", "rateName"),
            Map.entry("baseRate", "baseRate"),
            Map.entry("minimumWeight", "minimumWeight"),
            Map.entry("effectiveFrom", "effectiveFrom"),
            Map.entry("effectiveTo", "effectiveTo"),
            Map.entry("status", "status"),
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedDate", "updatedAt"));

    private static final int MAX_PAGE_SIZE = 100;

    private final RateService rateService;
    private final RateMapper mapper;

    @PostMapping
    @Operation(summary = "Create a rate", description = "`COMPANY_ADMIN`. A new rate always "
            + "starts ACTIVE, so its route must already be active and its weight slab must "
            + "not overlap another active rate for the same route, service type, package "
            + "type and payment mode.")
    public ResponseEntity<ApiResponse<RateResponse>> create(
            @Valid @RequestBody CreateRateRequest request) {
        Rate created = rateService.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/rates/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Rate created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a rate", description = "Full replacement of the editable "
            + "fields. `version` required; a stale value returns 409. `rateCode` is "
            + "immutable; status has its own endpoints.")
    public ApiResponse<RateResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateRateRequest request) {
        Rate updated = rateService.update(id, mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(updated), "Rate updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a rate")
    public ApiResponse<RateResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(rateService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List rates", description = """
            Paged, sorted, filtered, searchable. Sort: `rateCode`, `rateName`, `baseRate`,
            `minimumWeight`, `effectiveFrom`, `effectiveTo`, `status`, `createdDate`,
            `updatedDate`. `size` capped at 100.
            """)
    public ApiResponse<PageResponse<RateSummaryResponse>> list(
            @Valid @ParameterObject RateSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "rateCode") Pageable pageable) {
        Page<Rate> page = rateService.search(mapper.toCriteria(search), sanitise(pageable));
        return ApiResponse.success(PageResponse.from(page, mapper::toSummary));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a rate", description = "`COMPANY_ADMIN`. Refused if the "
            + "rate's route is inactive, or its weight slab now overlaps another active "
            + "rate for the same combination. Idempotent.")
    public ApiResponse<RateResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(rateService.activate(id)), "Rate activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a rate", description = "`COMPANY_ADMIN`. Withdraws it "
            + "from pricing; shipments already booked against it are unaffected. Idempotent.")
    public ApiResponse<RateResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(rateService.deactivate(id)), "Rate deactivated");
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate freight", description = "Prices a shipment without "
            + "booking it. Resolves the route from the booking/delivery branch pair, "
            + "matches the ACTIVE rate whose weight slab covers the actual weight and "
            + "whose effective window covers the booking date (default today), and returns "
            + "the full breakdown.")
    public ApiResponse<RateCalculationResponse> calculate(
            @Valid @RequestBody RateCalculationRequest request) {
        var result = rateService.calculate(mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(result), "Rate calculated");
    }

    // -------------------------------------------------------------------- helpers

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
        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.asc("rateCode")) : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
