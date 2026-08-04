package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateVehicleTypeRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.VehicleTypeResponse;
import com.courier.modules.master.api.dto.UpdateVehicleTypeRequest;
import com.courier.modules.master.application.VehicleTypeService;
import com.courier.modules.master.domain.VehicleType;
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
 * Vehicle types — BIKE, AUTO, PICKUP, TRUCK, CONTAINER and whatever else a company runs.
 *
 * <p>A flat catalogue: no parent, so no hierarchy rules. {@code POST /api/v1/master/bootstrap}
 * seeds the standard set.
 */
@RestController
@RequestMapping("/api/v1/master/vehicle-types")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Master Data - Vehicle Types", description = "Vehicle type master")
public class VehicleTypeController {

    private final VehicleTypeService service;
    private final VehicleTypeMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a vehicle type",
            description = "`COMPANY_ADMIN` only. Code and name are unique within the company.")
    public ResponseEntity<ApiResponse<VehicleTypeResponse>> create(
            @Valid @RequestBody CreateVehicleTypeRequest request) {
        VehicleType created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/vehicle-types/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Vehicle type created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a vehicle type",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<VehicleTypeResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateVehicleTypeRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Vehicle type updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a vehicle type", description = "Any authenticated company user.")
    public ApiResponse<VehicleTypeResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List vehicle types",
            description = """
                    Paged, sorted, filtered, searchable. Sort: `code`, `name`, `status`,
                    `displayOrder`, `capacityKg`, `createdDate`, `updatedDate`.
                    """)
    public ApiResponse<PageResponse<VehicleTypeResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only types that need a commercial permit")
            @RequestParam(required = false) Boolean requiresPermit,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("requiresPermit", requiresPermit);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable, MasterSortSupport.withExtra("capacityKg", "capacityKg")))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a vehicle type",
            description = "Soft delete, `COMPANY_ADMIN` only. The code stays reserved.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Vehicle type deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a vehicle type",
            description = "`COMPANY_ADMIN`. Idempotent.")
    public ApiResponse<VehicleTypeResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Vehicle type activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a vehicle type",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<VehicleTypeResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Vehicle type deactivated");
    }
}
