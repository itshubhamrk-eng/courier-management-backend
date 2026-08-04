package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateWeightSlabRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.WeightSlabResponse;
import com.courier.modules.master.api.dto.UpdateWeightSlabRequest;
import com.courier.modules.master.application.WeightSlabService;
import com.courier.modules.master.domain.WeightSlab;
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
import com.courier.modules.master.domain.WeightUnit;

import java.util.UUID;

/**
 * Weight slabs, the bands the rate master prices against.
 *
 * <p>Each band is half-open, {@code [min, max)} — a 1 kg parcel falls in 1-5, not 0-1 — and
 * no two <b>active</b> slabs of the same unit may overlap. The overlap rule is enforced in
 * the service, on save <i>and</i> on activation, because MySQL has no exclusion constraint
 * and because deactivating a slab, adding an overlapping one and reactivating the first
 * would otherwise walk straight around it.
 */
@RestController
@RequestMapping("/api/v1/master/weight-slabs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Master Data - Weight Slabs", description = "Weight slab master")
public class WeightSlabController {

    private final WeightSlabService service;
    private final WeightSlabMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a weight slab",
            description = "`COMPANY_ADMIN` only. Refused with 422 if the band overlaps another active slab of the same unit, or if the maximum is not above the minimum.")
    public ResponseEntity<ApiResponse<WeightSlabResponse>> create(
            @Valid @RequestBody CreateWeightSlabRequest request) {
        WeightSlab created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/weight-slabs/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Weight slab created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a weight slab",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<WeightSlabResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateWeightSlabRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Weight slab updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a weight slab", description = "Any authenticated company user.")
    public ApiResponse<WeightSlabResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List weight slabs",
            description = """
                    Paged, sorted, filtered, searchable. Filter by `weightUnit`. Sort: `code`,
                    `name`, `status`, `displayOrder`, `minWeight`, `createdDate`, `updatedDate`.
                    """)
    public ApiResponse<PageResponse<WeightSlabResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only slabs measured in this unit")
            @RequestParam(required = false) WeightUnit weightUnit,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("weightUnit", weightUnit);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable, MasterSortSupport.withExtra("minWeight", "minWeight")))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a weight slab",
            description = "Soft delete, `COMPANY_ADMIN` only. The code stays reserved.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Weight slab deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a weight slab",
            description = "`COMPANY_ADMIN`. Refused with 422 if the band now overlaps an active slab. Idempotent.")
    public ApiResponse<WeightSlabResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Weight slab activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a weight slab",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<WeightSlabResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Weight slab deactivated");
    }
}
