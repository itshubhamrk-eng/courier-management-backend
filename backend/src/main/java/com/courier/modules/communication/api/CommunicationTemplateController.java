package com.courier.modules.communication.api;

import com.courier.modules.communication.api.dto.CommunicationTemplatePreviewResponse;
import com.courier.modules.communication.api.dto.CommunicationTemplateResponse;
import com.courier.modules.communication.api.dto.CreateCommunicationTemplateRequest;
import com.courier.modules.communication.api.dto.UpdateCommunicationTemplateRequest;
import com.courier.modules.communication.application.CommunicationTemplateService;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/** Template by (event, channel) — e.g. "SHIPMENT_BOOKED + WHATSAPP". Company Admin can
 *  create/edit/enable/disable/preview; the four default events are seeded on first read. */
@RestController
@RequestMapping("/api/v1/communication/templates")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Communication Templates", description = "Per-event, per-channel message templates")
public class CommunicationTemplateController {

    private final CommunicationTemplateService service;
    private final CommunicationMapper mapper;

    @GetMapping
    @Operation(summary = "List templates", description = "Seeds the four default events x three "
            + "channels on first read for a company with none yet.")
    public ApiResponse<List<CommunicationTemplateResponse>> list() {
        return ApiResponse.success(service.list().stream().map(mapper::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a template")
    public ApiResponse<CommunicationTemplateResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a template", description = "One per (eventType, channel) per company.")
    public ResponseEntity<ApiResponse<CommunicationTemplateResponse>> create(
            @Valid @RequestBody CreateCommunicationTemplateRequest request) {
        CommunicationTemplate created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/communication/templates/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "Template created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a template", description = "Full replacement of the editable fields, "
            + "including status (ACTIVE/INACTIVE) — the per-event-per-channel enable/disable switch.")
    public ApiResponse<CommunicationTemplateResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateCommunicationTemplateRequest request) {
        return ApiResponse.success(mapper.toResponse(service.update(id, mapper.toCommand(request))),
                "Template updated");
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "Preview a template", description = "Rendered against synthetic sample "
            + "shipment data — no real shipment required.")
    public ApiResponse<CommunicationTemplatePreviewResponse> preview(@PathVariable UUID id) {
        var rendered = service.preview(id);
        return ApiResponse.success(new CommunicationTemplatePreviewResponse(rendered.subject(), rendered.content()));
    }
}
