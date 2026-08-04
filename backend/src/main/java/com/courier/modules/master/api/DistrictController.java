package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateDistrictRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.DistrictResponse;
import com.courier.modules.master.api.dto.UpdateDistrictRequest;
import com.courier.modules.master.application.DistrictService;
import com.courier.modules.master.domain.District;
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
 * Districts, within a state. Same parent rules as the state master.
 */
@RestController
@RequestMapping("/api/v1/global-masters/districts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Global Masters - Districts", description = "District master, within a state. Global: SUPER_ADMIN writes, anyone signed in reads.")
public class DistrictController {

    private final DistrictService service;
    private final DistrictMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a district",
            description = "`COMPANY_ADMIN` only. The state must belong to this company and be active. The name is unique within the state, the code within the company.")
    public ResponseEntity<ApiResponse<DistrictResponse>> create(
            @Valid @RequestBody CreateDistrictRequest request) {
        District created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/districts/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "District created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a district",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<DistrictResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateDistrictRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "District updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a district", description = "Any authenticated company user.")
    public ApiResponse<DistrictResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List districts",
            description = """
                    Paged, sorted, filtered, searchable. Filter by `stateId` to populate a city
                    picker. Sort: `code`, `name`, `status`, `displayOrder`, `createdDate`,
                    `updatedDate`.
                    """)
    public ApiResponse<PageResponse<DistrictResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only districts of this state")
            @RequestParam(required = false) UUID stateId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("stateId", stateId);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a district",
            description = "Soft delete, `COMPANY_ADMIN` only. Refused with 422 while the district still has cities.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("District deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a district",
            description = "`COMPANY_ADMIN`. Refused with 422 if the parent state is inactive. Idempotent.")
    public ApiResponse<DistrictResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "District activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a district",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<DistrictResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "District deactivated");
    }
}
