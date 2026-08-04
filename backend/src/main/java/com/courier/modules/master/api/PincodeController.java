package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreatePincodeRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.PincodeResponse;
import com.courier.modules.master.api.dto.UpdatePincodeRequest;
import com.courier.modules.master.application.PincodeService;
import com.courier.modules.master.domain.Pincode;
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
 * Pincodes, the leaf of the geography hierarchy.
 *
 * <p>The {@code code} is the postal code itself and is digits only; {@code name} is the
 * post office label. One pincode belongs to exactly one area and exists once per company.
 *
 * <p>Setting {@code serviceable} false forces the COD, prepaid and pickup flags false with
 * it: a pincode nobody delivers to cannot offer cash on delivery.
 */
@RestController
@RequestMapping("/api/v1/global-masters/pincodes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Global Masters - Pincodes", description = "Pincode master, within an area. Global: SUPER_ADMIN writes, anyone signed in reads.")
public class PincodeController {

    private final PincodeService service;
    private final PincodeMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a pincode",
            description = "`COMPANY_ADMIN` only. The area must belong to this company and be active. The pincode is unique within the company.")
    public ResponseEntity<ApiResponse<PincodeResponse>> create(
            @Valid @RequestBody CreatePincodeRequest request) {
        Pincode created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/pincodes/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Pincode created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a pincode",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<PincodeResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdatePincodeRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Pincode updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a pincode", description = "Any authenticated company user.")
    public ApiResponse<PincodeResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List pincodes",
            description = """
                    Paged, sorted, filtered, searchable. Filter by `areaId`, by `serviceable`
                    or by delivery `zone` — the three a booking screen asks for. Sort:
                    `code`, `name`, `status`, `displayOrder`, `createdDate`, `updatedDate`.
                    """)
    public ApiResponse<PageResponse<PincodeResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only pincodes of this area")
            @RequestParam(required = false) UUID areaId,
            @Parameter(description = "Only serviceable (or only unserviceable) pincodes")
            @RequestParam(required = false) Boolean serviceable,
            @Parameter(description = "Delivery zone, e.g. LOCAL")
            @RequestParam(required = false) String zone,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("areaId", areaId).with("serviceable", serviceable).with("zone", zone);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a pincode",
            description = "Soft delete, `COMPANY_ADMIN` only. The pincode stays reserved.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Pincode deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a pincode",
            description = "`COMPANY_ADMIN`. Refused with 422 if the parent area is inactive. Idempotent.")
    public ApiResponse<PincodeResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Pincode activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a pincode",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<PincodeResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Pincode deactivated");
    }
}
