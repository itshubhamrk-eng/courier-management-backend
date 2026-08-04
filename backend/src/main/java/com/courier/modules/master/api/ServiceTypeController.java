package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateServiceTypeRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.ServiceTypeResponse;
import com.courier.modules.master.api.dto.UpdateServiceTypeRequest;
import com.courier.modules.master.application.ServiceTypeService;
import com.courier.modules.master.domain.ServiceType;
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
 * Service types — STANDARD, EXPRESS, SAME_DAY, ECONOMY.
 *
 * <p>{@code deliveryDays} is the promise quoted to the customer and {@code cutoffTime} the
 * last moment a booking still makes it. Zero days means same day.
 */
@RestController
@RequestMapping("/api/v1/master/service-types")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Master Data - Service Types", description = "Service type master")
public class ServiceTypeController {

    private final ServiceTypeService service;
    private final ServiceTypeMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a service type",
            description = "`COMPANY_ADMIN` only. Code and name are unique within the company.")
    public ResponseEntity<ApiResponse<ServiceTypeResponse>> create(
            @Valid @RequestBody CreateServiceTypeRequest request) {
        ServiceType created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/service-types/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Service type created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a service type",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<ServiceTypeResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateServiceTypeRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Service type updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a service type", description = "Any authenticated company user.")
    public ApiResponse<ServiceTypeResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List service types",
            description = """
                    Paged, sorted, filtered, searchable. Filter by `express`. Sort: `code`,
                    `name`, `status`, `displayOrder`, `priority`, `createdDate`, `updatedDate`.
                    """)
    public ApiResponse<PageResponse<ServiceTypeResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only express (or only non-express) services")
            @RequestParam(required = false) Boolean express,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("express", express);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable, MasterSortSupport.withExtra("priority", "priority")))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a service type",
            description = "Soft delete, `COMPANY_ADMIN` only. The code stays reserved.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Service type deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a service type",
            description = "`COMPANY_ADMIN`. Idempotent.")
    public ApiResponse<ServiceTypeResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Service type activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a service type",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<ServiceTypeResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Service type deactivated");
    }
}
