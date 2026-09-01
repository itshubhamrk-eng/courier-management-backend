package com.courier.modules.communication.api;

import com.courier.modules.communication.api.dto.CommunicationSettingResponse;
import com.courier.modules.communication.api.dto.ConnectionTestResponse;
import com.courier.modules.communication.api.dto.UpsertCommunicationSettingRequest;
import com.courier.modules.communication.application.CommunicationSettingService;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Channel Settings — one card per channel (WhatsApp/SMS/Email): enable/disable, provider,
 *  config, Test Connection. Never exposes a stored secret, only whether one is set. */
@RestController
@RequestMapping("/api/v1/communication/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Communication Settings", description = "Per-channel master switch and provider config")
public class CommunicationSettingController {

    private final CommunicationSettingService service;
    private final CommunicationMapper mapper;

    @GetMapping
    @Operation(summary = "List channel settings", description = "Seeds all three channel rows "
            + "(WhatsApp/SMS/Email, enabled by default) on first read.")
    public ApiResponse<List<CommunicationSettingResponse>> list() {
        return ApiResponse.success(service.list().stream().map(mapper::toResponse).toList());
    }

    @GetMapping("/{channel}")
    @Operation(summary = "Get one channel's settings")
    public ApiResponse<CommunicationSettingResponse> get(@PathVariable CommunicationChannel channel) {
        return ApiResponse.success(mapper.toResponse(service.get(channel)));
    }

    @PutMapping("/{channel}")
    @Operation(summary = "Update a channel's settings", description = "`secret` is optional — blank "
            + "or omitted keeps the one already stored.")
    public ApiResponse<CommunicationSettingResponse> upsert(@PathVariable CommunicationChannel channel,
            @Valid @RequestBody UpsertCommunicationSettingRequest request) {
        return ApiResponse.success(mapper.toResponse(service.upsert(mapper.toCommand(channel, request))),
                "Settings saved");
    }

    @PostMapping("/{channel}/test-connection")
    @Operation(summary = "Test a channel's configuration", description = "Validates the configured "
            + "fields are complete, not a live vendor handshake.")
    public ApiResponse<ConnectionTestResponse> testConnection(@PathVariable CommunicationChannel channel) {
        var result = service.testConnection(channel);
        return ApiResponse.success(new ConnectionTestResponse(result.ok(), result.message()));
    }
}
