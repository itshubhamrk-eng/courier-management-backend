package com.courier.modules.communication.api;

import com.courier.modules.communication.api.dto.CommunicationLogResponse;
import com.courier.modules.communication.api.dto.CommunicationLogSearchRequest;
import com.courier.modules.communication.application.CommunicationLogService;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Show shipment/customer/event/channel/recipient/status/provider/sent-time/failure-reason.
 *  Actions: View (this controller's own reads), Retry Failed. */
@RestController
@RequestMapping("/api/v1/communication/logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Communication Logs", description = "Delivery attempts, one row per shipment/event/channel")
public class CommunicationLogController {

    private final CommunicationLogService service;
    private final CommunicationMapper mapper;

    @GetMapping
    @Operation(summary = "Search communication logs")
    public ApiResponse<PageResponse<CommunicationLogResponse>> list(
            @ParameterObject CommunicationLogSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<CommunicationLogResponse> page = service.search(mapper.toCriteria(search), pageable)
                .map(mapper::toResponse);
        return ApiResponse.success(PageResponse.from(page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one communication log entry")
    public ApiResponse<CommunicationLogResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping("/shipment/{shipmentId}")
    @Operation(summary = "Every communication attempt for one shipment",
            description = "Backs the Shipment Details Communication tab.")
    public ApiResponse<List<CommunicationLogResponse>> forShipment(@PathVariable UUID shipmentId) {
        return ApiResponse.success(service.forShipment(shipmentId).stream().map(mapper::toResponse).toList());
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a failed attempt", description = "Only a FAILED row may be retried; "
            + "requeues it immediately, bypassing its own backoff.")
    public ApiResponse<CommunicationLogResponse> retry(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.retry(id)), "Requeued for retry");
    }
}
