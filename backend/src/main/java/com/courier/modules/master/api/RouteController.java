package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateRouteRequest;
import com.courier.modules.master.api.dto.MasterSearchRequest;
import com.courier.modules.master.api.dto.RouteResponse;
import com.courier.modules.master.api.dto.UpdateRouteRequest;
import com.courier.modules.master.application.RouteService;
import com.courier.modules.master.domain.Route;
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
 * Routes: booking branch to delivery branch, with a distance and a transit promise.
 *
 * <p>Read later by Shipment Booking, the Rate Master and Manifest Planning, which is why it
 * is a master in its own right rather than three copies of the same branch pair.
 *
 * <p><b>Direction matters.</b> Pune to Mumbai and Mumbai to Pune are two routes: the
 * kilometres usually match, the transit days frequently do not. Only one route may exist
 * per ordered pair, and the two ends must differ.
 */
@RestController
@RequestMapping("/api/v1/master/routes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Master Data - Routes", description = "Route master, booking branch to delivery branch")
public class RouteController {

    private final RouteService service;
    private final RouteMasterMapper mapper;
    private final MasterCriteriaMapper criteriaMapper;

    @PostMapping
    @Operation(summary = "Create a route",
            description = "`COMPANY_ADMIN` only. Both branches must belong to this company and be active, and they must differ. A second route for the same ordered pair is refused with 422.")
    public ResponseEntity<ApiResponse<RouteResponse>> create(
            @Valid @RequestBody CreateRouteRequest request) {
        Route created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/master/routes/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Route created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a route",
            description = "Full replacement of the editable fields. `version` is required; "
                    + "a stale value returns 409. The code cannot be changed.")
    public ApiResponse<RouteResponse> update(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateRouteRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Route updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a route", description = "Any authenticated company user.")
    public ApiResponse<RouteResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List routes",
            description = """
                    Paged, sorted, filtered, searchable. Filter by `bookingBranchId` or
                    `deliveryBranchId` to see one branch's lanes. Sort: `code`, `name`,
                    `status`, `displayOrder`, `transitDays`, `createdDate`, `updatedDate`.
                    """)
    public ApiResponse<PageResponse<RouteResponse>> list(
            @Valid @ParameterObject MasterSearchRequest search,
            @Parameter(description = "Only routes starting at this branch")
            @RequestParam(required = false) UUID bookingBranchId,
            @Parameter(description = "Only routes ending at this branch")
            @RequestParam(required = false) UUID deliveryBranchId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        MasterDataCriteria criteria = criteriaMapper.toCriteria(search).with("bookingBranchId", bookingBranchId)
                .with("deliveryBranchId", deliveryBranchId);

        return ApiResponse.success(mapper.toPage(
                service.search(criteria, MasterSortSupport.sanitise(pageable, MasterSortSupport.withExtra("transitDays", "transitDays")))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a route",
            description = "Soft delete, `COMPANY_ADMIN` only. The code and the branch pair stay reserved.")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Route deleted");
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a route",
            description = "`COMPANY_ADMIN`. Idempotent.")
    public ApiResponse<RouteResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.activate(id)), "Route activated");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a route",
            description = "`COMPANY_ADMIN`. Withdraws it from the pickers; existing "
                    + "references keep resolving. Idempotent.")
    public ApiResponse<RouteResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.deactivate(id)), "Route deactivated");
    }
}
