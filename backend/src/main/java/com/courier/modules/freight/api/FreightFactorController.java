package com.courier.modules.freight.api;

import com.courier.modules.freight.api.dto.CreateFreightFactorRequest;
import com.courier.modules.freight.api.dto.FreightCalculationRequest;
import com.courier.modules.freight.api.dto.FreightCalculationResponse;
import com.courier.modules.freight.api.dto.FreightFactorResponse;
import com.courier.modules.freight.api.dto.FreightFactorSearchRequest;
import com.courier.modules.freight.api.dto.UpdateFreightFactorRequest;
import com.courier.modules.freight.application.FreightFactorService;
import com.courier.modules.freight.domain.FreightFactor;
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
 * Freight Factor: a standalone, company-level pricing grid keyed on distance range x
 * weight range, independent of Rate Master/Pricing Engine. Enforced on
 * {@link FreightFactorService}: {@code COMPANY_ADMIN} creates, updates, activates and
 * deactivates; every authenticated user of the company reads and calculates.
 */
@RestController
@RequestMapping("/api/v1/freight-factors")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Freight Factor", description = "Company distance x weight freight pricing grid")
public class FreightFactorController {

    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("fromKm", "fromKm"),
            Map.entry("toKm", "toKm"),
            Map.entry("fromWeight", "fromWeight"),
            Map.entry("toWeight", "toWeight"),
            Map.entry("factor", "factor"),
            Map.entry("status", "status"),
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedDate", "updatedAt"));

    private static final int MAX_PAGE_SIZE = 100;

    private final FreightFactorService freightFactorService;
    private final FreightFactorMapper mapper;

    @PostMapping
    @Operation(summary = "Create a freight factor cell", description = "`COMPANY_ADMIN`. "
            + "A new cell always starts ACTIVE, so its distance range and weight range "
            + "must not both overlap another active cell.")
    public ResponseEntity<ApiResponse<FreightFactorResponse>> create(
            @Valid @RequestBody CreateFreightFactorRequest request) {
        FreightFactor created = freightFactorService.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/freight-factors/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Freight factor created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a freight factor cell", description = "Full replacement "
            + "of the editable fields. `version` required; a stale value returns 409. "
            + "Status has its own endpoints.")
    public ApiResponse<FreightFactorResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateFreightFactorRequest request) {
        FreightFactor updated = freightFactorService.update(id, mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(updated), "Freight factor updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a freight factor cell")
    public ApiResponse<FreightFactorResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(freightFactorService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List freight factor cells", description = """
            Paged, sorted, filtered by status. Sort: `fromKm`, `toKm`, `fromWeight`,
            `toWeight`, `factor`, `status`, `createdDate`, `updatedDate`. `size` capped at
            100.
            """)
    public ApiResponse<PageResponse<FreightFactorResponse>> list(
            @Valid @ParameterObject FreightFactorSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "fromKm") Pageable pageable) {
        Page<FreightFactor> page = freightFactorService.search(mapper.toCriteria(search), sanitise(pageable));
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a freight factor cell", description = "`COMPANY_ADMIN`. "
            + "Refused if its distance range and weight range now both overlap another "
            + "active cell. Idempotent.")
    public ApiResponse<FreightFactorResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(freightFactorService.activate(id)),
                "Freight factor activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a freight factor cell", description = "`COMPANY_ADMIN`. "
            + "Withdraws it from calculation. Idempotent.")
    public ApiResponse<FreightFactorResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(freightFactorService.deactivate(id)),
                "Freight factor deactivated");
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate freight", description = "Resolves the distance between "
            + "the two branches (Address Distance module, cache-or-resolve), matches the "
            + "ACTIVE cell whose distance range and weight range both cover the request, "
            + "and returns freight = factor * weight.")
    public ApiResponse<FreightCalculationResponse> calculate(
            @Valid @RequestBody FreightCalculationRequest request) {
        var result = freightFactorService.calculate(mapper.toCommand(request));
        return ApiResponse.success(mapper.toResponse(result), "Freight calculated");
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
        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.asc("fromKm")) : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
