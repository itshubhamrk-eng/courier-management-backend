package com.courier.modules.crossing.api;

import com.courier.modules.crossing.api.dto.CrossingResponse;
import com.courier.modules.crossing.api.dto.CrossingSearchRequest;
import com.courier.modules.crossing.api.dto.UpdateCrossingStatusRequest;
import com.courier.modules.crossing.application.CrossingService;
import com.courier.modules.crossing.domain.CrossingDetail;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A shipment's crossing through an intermediate branch/hub. There is no {@code POST}
 * here — a crossing is created only by Shipment Booking, when the booking desk picks a
 * crossing branch (see {@code ShipmentServiceImpl.create}), never as a standalone write.
 */
@RestController
@RequestMapping("/api/v1/crossings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Crossing", description = "A shipment's transit through an intermediate branch/hub")
public class CrossingController {

    private final CrossingService service;
    private final CrossingMapper mapper;

    @GetMapping("/{id}")
    public ApiResponse<CrossingResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List crossings", description = "Filter by shipment, crossing branch, and/or status.")
    public ApiResponse<PageResponse<CrossingResponse>> list(
            @ParameterObject CrossingSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<CrossingDetail> page = service.search(mapper.toCriteria(search), pageable);
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move a crossing to a new status",
            description = "COMPANY_ADMIN / BRANCH_MANAGER / HUB_MANAGER / OPERATOR. Refused "
                    + "once the crossing is COMPLETED or CANCELLED.")
    public ApiResponse<CrossingResponse> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateCrossingStatusRequest request) {
        CrossingDetail updated = service.updateStatus(id, request.status(), request.remarks());
        return ApiResponse.success(mapper.toResponse(updated), "Crossing status updated");
    }
}
