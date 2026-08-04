package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateCityRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.CityResponse;
import com.courier.modules.master.api.dto.UpdateCityRequest;
import com.courier.modules.master.application.CityService;
import com.courier.modules.master.domain.City;
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
 * Cities, within a district.
 *
 * <p>{@code metro} and {@code cityTier} are commercial gradings the rate master will price
 * against; they carry no operational meaning here.
 */
@RestController
@RequestMapping("/api/v1/global-masters/cities")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Global Masters - Cities", description = "City master, within a district. Global: SUPER_ADMIN writes, anyone signed in reads.")
public class CityController {

    private final CityService service;
    private final CityMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a city",
            description = "`COMPANY_ADMIN` only. The district must belong to this company and be active.")
    public ResponseEntity<ApiResponse<CityResponse>> create(
            @Valid @RequestBody CreateCityRequest request) {
        City created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/cities/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "City created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a city",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<CityResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateCityRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "City updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a city", description = "Any authenticated company user.")
    public ApiResponse<CityResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List cities",
            description = """
                    Paged, sorted, filtered, searchable. Filter by `districtId` for an area
                    picker, or by `metro`. Sort: `code`, `name`, `status`, `displayOrder`,
                    `createdDate`, `updatedDate`.
                    """)
    public ApiResponse<PageResponse<CityResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only cities of this district")
            @RequestParam(required = false) UUID districtId,
            @Parameter(description = "Only metro (or only non-metro) cities")
            @RequestParam(required = false) Boolean metro,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("districtId", districtId).with("metro", metro);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a city",
            description = "Soft delete, `COMPANY_ADMIN` only. Refused with 422 while the city still has areas.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("City deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a city",
            description = "`COMPANY_ADMIN`. Refused with 422 if the parent district is inactive. Idempotent.")
    public ApiResponse<CityResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "City activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a city",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<CityResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "City deactivated");
    }
}
