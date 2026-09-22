package com.courier.modules.crossing.api;

import com.courier.modules.crossing.api.dto.ExceptionSearchRequest;
import com.courier.modules.crossing.api.dto.HubDashboardResponse;
import com.courier.modules.crossing.api.dto.OutScanRequest;
import com.courier.modules.crossing.api.dto.RaiseExceptionRequest;
import com.courier.modules.crossing.api.dto.ResolveExceptionRequest;
import com.courier.modules.crossing.api.dto.ShipmentExceptionResponse;
import com.courier.modules.crossing.application.HubOperationsService;
import com.courier.modules.crossing.domain.ShipmentException;
import com.courier.modules.shipment.api.dto.MovementOutcomeResponse;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The genuinely new part of Hub Operations — out-scan and exceptions. In-scan reuses
 * {@code POST /shipment-movement/in-scan}; Load Sheet creation, vehicle/driver and
 * dispatch reuse {@code /manifests}; the shipments-at-hub list and destination grouping
 * reuse {@code GET /shipments} and {@code GET /manifests/eligible-destinations} — none of
 * those are duplicated here.
 */
@RestController
@RequestMapping("/api/v1/hub-operations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Hub Operations", description = "Out-scan, exceptions and the hub dashboard")
public class HubOperationsController {

    private final HubOperationsService service;
    private final HubOperationsMapper mapper;

    @PostMapping("/out-scan")
    @Operation(summary = "Out-scan shipments at a hub",
            description = "Confirms every given tracking number is physically present and attached to "
                    + "this Load Sheet. A tracking number already out-scanned for this manifest is rejected "
                    + "as a duplicate.")
    public ApiResponse<List<MovementOutcomeResponse>> outScan(@Valid @RequestBody OutScanRequest request) {
        List<MovementOutcomeResponse> outcomes = service
                .outScan(request.manifestId(), request.hubBranchId(), request.trackingNumbers())
                .stream()
                .map(o -> new MovementOutcomeResponse(o.reference(), o.success(), o.message()))
                .toList();
        return ApiResponse.success(outcomes);
    }

    @PostMapping("/exceptions")
    @Operation(summary = "Raise a Hub Operations exception",
            description = "Missing/Damaged/Short/Wrong Destination/Misrouted/On Hold. Never changes the "
                    + "shipment's own status.")
    public ApiResponse<ShipmentExceptionResponse> raiseException(@Valid @RequestBody RaiseExceptionRequest request) {
        ShipmentException saved = service.raiseException(
                request.shipmentId(), request.hubBranchId(), request.exceptionType(), request.remarks());
        return ApiResponse.success(mapper.toResponse(saved), "Exception raised");
    }

    @PatchMapping("/exceptions/{id}/resolve")
    @Operation(summary = "Resolve an open exception")
    public ApiResponse<ShipmentExceptionResponse> resolveException(@PathVariable UUID id,
            @Valid @RequestBody ResolveExceptionRequest request) {
        ShipmentException resolved = service.resolveException(id, request.resolutionRemarks());
        return ApiResponse.success(mapper.toResponse(resolved), "Exception resolved");
    }

    @GetMapping("/exceptions/{id}")
    public ApiResponse<ShipmentExceptionResponse> getException(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getException(id)));
    }

    @GetMapping("/exceptions")
    @Operation(summary = "List exceptions", description = "Filter by shipment, hub, and/or status.")
    public ApiResponse<PageResponse<ShipmentExceptionResponse>> listExceptions(
            @ParameterObject ExceptionSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<ShipmentException> page = service.searchExceptions(mapper.toCriteria(search), pageable);
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Hub Operations dashboard", description = "The nine figures for one hub, today.")
    public ApiResponse<HubDashboardResponse> dashboard(@RequestParam UUID hubBranchId) {
        return ApiResponse.success(mapper.toResponse(service.dashboard(hubBranchId)));
    }
}
