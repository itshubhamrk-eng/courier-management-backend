package com.courier.modules.ewaybill.api;

import com.courier.modules.ewaybill.api.dto.CreateEwayBillRequest;
import com.courier.modules.ewaybill.api.dto.EwayBillResponse;
import com.courier.modules.ewaybill.api.dto.EwayBillUploadResponse;
import com.courier.modules.ewaybill.api.dto.UpdateEwayBillRequest;
import com.courier.modules.ewaybill.application.EwayBillService;
import com.courier.modules.ewaybill.domain.EwayBill;
import com.courier.modules.ewaybill.domain.EwayBillStatus;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import com.courier.shared.exception.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

/**
 * E-Way Bill Management. Standalone CRUD + lifecycle for attaching, amending, validating,
 * documenting and cancelling an E-Way Bill against an already-booked shipment.
 *
 * <p>The booking-time gate itself — invoice value over the company's own threshold blocks
 * AWB generation until an E-Way Bill validates — is enforced inside {@code POST}/
 * {@code PUT /shipments} (see {@code ShipmentController}), not here; these endpoints are
 * for managing an E-Way Bill afterward (or optionally attaching/amending one for a
 * shipment that never needed one at booking time).
 */
@RestController
@RequestMapping("/api/v1/eway-bills")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "E-Way Bill", description = "E-Way Bill Management for booked shipments")
public class EwayBillController {

    private final EwayBillService service;
    private final EwayBillMapper mapper;

    @PostMapping
    @Operation(summary = "Attach an E-Way Bill to a shipment")
    public ResponseEntity<ApiResponse<EwayBillResponse>> create(
            @Valid @RequestBody CreateEwayBillRequest request) {
        EwayBill created = service.create(mapper.toCommand(request));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/eway-bills/{id}")
                        .buildAndExpand(created.getId()).toUri())
                .body(ApiResponse.success(mapper.toResponse(created), "E-Way Bill created"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an E-Way Bill", description = "Refused once CANCELLED. "
            + "`version` required; a stale value returns 409.")
    public ApiResponse<EwayBillResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateEwayBillRequest request) {
        return ApiResponse.success(
                mapper.toResponse(service.update(id, mapper.toCommand(request))), "E-Way Bill updated");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch an E-Way Bill")
    public ApiResponse<EwayBillResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.getById(id)));
    }

    @GetMapping
    @Operation(summary = "List E-Way Bills", description = "Filterable by `shipmentId` and/or `status`.")
    public ApiResponse<PageResponse<EwayBillResponse>> search(
            @RequestParam(required = false) UUID shipmentId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        EwayBillStatus parsedStatus = parseStatus(status);
        Page<EwayBill> page = service.search(shipmentId, parsedStatus, pageable);
        return ApiResponse.success(PageResponse.from(page, mapper::toResponse));
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate an E-Way Bill",
            description = "Re-checks the row's own current fields and moves it to "
                    + "`VALIDATED` or `INVALID`. Only a `VALIDATED` E-Way Bill lets AWB "
                    + "generation proceed for a shipment where one is mandatory.")
    public ApiResponse<EwayBillResponse> validate(@PathVariable UUID id) {
        return ApiResponse.success(mapper.toResponse(service.validate(id)), "E-Way Bill validated");
    }

    @PostMapping("/{id}/upload")
    @Operation(summary = "Upload the E-Way Bill document", description = "PDF, JPG or PNG only.")
    public ApiResponse<EwayBillUploadResponse> upload(@PathVariable UUID id,
                                                       @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded file could not be read. Please retry.");
        }
        String url = service.upload(id, new EwayBillService.UploadCommand(
                content, file.getOriginalFilename(), file.getContentType()));
        return ApiResponse.success(new EwayBillUploadResponse(url), "Document uploaded");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an E-Way Bill", description = "Terminal — a cancelled row "
            + "may not be edited, validated or documented again.")
    public ApiResponse<EwayBillResponse> cancel(@PathVariable UUID id,
                                                @RequestParam(required = false) String remarks) {
        return ApiResponse.success(mapper.toResponse(service.cancel(id, remarks)), "E-Way Bill cancelled");
    }

    private EwayBillStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EwayBillStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("No such E-Way Bill status: " + raw);
        }
    }
}
