package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreatePackageTypeRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.PackageTypeResponse;
import com.courier.modules.master.api.dto.UpdatePackageTypeRequest;
import com.courier.modules.master.application.PackageTypeService;
import com.courier.modules.master.domain.PackageType;
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
 * Package types — DOCUMENT, PARCEL, BOX, BAG, PALLET.
 *
 * <p>{@code documentType} is not just a label: documents are rated on a flat slab rather
 * than by weight, and skip the dimension capture a parcel needs.
 */
@RestController
@RequestMapping("/api/v1/master/package-types")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Master Data - Package Types", description = "Package type master")
public class PackageTypeController {

    private final PackageTypeService service;
    private final PackageTypeMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a package type",
            description = "`COMPANY_ADMIN` only. Code and name are unique within the company.")
    public ResponseEntity<ApiResponse<PackageTypeResponse>> create(
            @Valid @RequestBody CreatePackageTypeRequest request) {
        PackageType created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/package-types/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Package type created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a package type",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<PackageTypeResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdatePackageTypeRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Package type updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a package type", description = "Any authenticated company user.")
    public ApiResponse<PackageTypeResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List package types",
            description = """
                    Paged, sorted, filtered, searchable. Filter by `documentType`. Sort: `code`,
                    `name`, `status`, `displayOrder`, `createdDate`, `updatedDate`.
                    """)
    public ApiResponse<PageResponse<PackageTypeResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only document (or only non-document) types")
            @RequestParam(required = false) Boolean documentType,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("documentType", documentType);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a package type",
            description = "Soft delete, `COMPANY_ADMIN` only. The code stays reserved.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Package type deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a package type",
            description = "`COMPANY_ADMIN`. Idempotent.")
    public ApiResponse<PackageTypeResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Package type activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a package type",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<PackageTypeResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Package type deactivated");
    }
}
