package com.courier.modules.communication.api;

import com.courier.modules.communication.api.dto.CommunicationDashboardResponse;
import com.courier.modules.communication.application.CommunicationDashboardService;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Total Sent/Delivered/Failed/Pending, plus today's per-channel breakdown. */
@RestController
@RequestMapping("/api/v1/communication/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Communication Dashboard", description = "Today's send/delivery/failure statistics")
public class CommunicationDashboardController {

    private final CommunicationDashboardService service;
    private final CommunicationMapper mapper;

    @GetMapping
    @Operation(summary = "Today's communication statistics")
    public ApiResponse<CommunicationDashboardResponse> today() {
        return ApiResponse.success(mapper.toResponse(service.today()));
    }
}
