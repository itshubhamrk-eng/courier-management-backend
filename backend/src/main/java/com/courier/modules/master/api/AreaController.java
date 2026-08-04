package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateAreaRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.AreaResponse;
import com.courier.modules.master.api.dto.UpdateAreaRequest;
import com.courier.modules.master.application.AreaService;
import com.courier.modules.master.domain.Area;
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
 * Areas, within a city — the level a delivery boy is assigned to.
 *
 * <p>One area belongs to exactly one city: a single non-null column, not a join table.
 */
@RestController
@RequestMapping("/api/v1/global-masters/areas")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Global Masters - Areas", description = "Area master, within a city. Global: SUPER_ADMIN writes, anyone signed in reads.")
public class AreaController {

    private final AreaService service;
    private final AreaMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create an area",
            description = "`COMPANY_ADMIN` only. The city must belong to this company and be active.")
    public ResponseEntity<ApiResponse<AreaResponse>> create(
            @Valid @RequestBody CreateAreaRequest request) {
        Area created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/areas/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Area created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an area",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<AreaResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateAreaRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Area updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch an area", description = "Any authenticated company user.")
    public ApiResponse<AreaResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List areas",
            description = """
                    Paged, sorted, filtered, searchable. Filter by `cityId` to populate a
                    pincode picker. Sort: `code`, `name`, `status`, `displayOrder`,
                    `createdDate`, `updatedDate`.
                    """)
    public ApiResponse<PageResponse<AreaResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only areas of this city")
            @RequestParam(required = false) UUID cityId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("cityId", cityId);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an area",
            description = "Soft delete, `COMPANY_ADMIN` only. Refused with 422 while the area still has pincodes.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Area deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate an area",
            description = "`COMPANY_ADMIN`. Refused with 422 if the parent city is inactive. Idempotent.")
    public ApiResponse<AreaResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Area activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate an area",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<AreaResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Area deactivated");
    }
}
