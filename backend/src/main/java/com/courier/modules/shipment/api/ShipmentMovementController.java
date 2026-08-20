package com.courier.modules.shipment.api;

import com.courier.modules.manifest.application.ManifestService;
import com.courier.modules.manifest.domain.Manifest;
import com.courier.modules.shipment.api.dto.BulkMovementResponse;
import com.courier.modules.shipment.api.dto.DeliverRequest;
import com.courier.modules.shipment.api.dto.DispatchManifestRequest;
import com.courier.modules.shipment.api.dto.DispatchManifestResponse;
import com.courier.modules.shipment.api.dto.InScanRequest;
import com.courier.modules.shipment.api.dto.OutForDeliveryRequest;
import com.courier.modules.shipment.api.dto.PodUploadResponse;
import com.courier.modules.shipment.api.dto.ShipmentResponse;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.exception.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * The four remaining movement steps, in business-flow order: Dispatch -&gt; In Scan -&gt;
 * Out For Delivery -&gt; Deliver. Out Scan is gone as its own step (V20, on direct
 * request) — adding a shipment to a manifest already is "loading sheet created", so Dispatch
 * now reads {@code MANIFEST_CREATED} shipments directly rather than a separately-scanned
 * subset. Every write is a {@link ShipmentService} method except Dispatch, which is
 * {@link ManifestService}'s own (it owns the manifest-side mutation) — see
 * {@code ManifestServiceImpl}'s class doc for why the dependency runs only that one
 * direction. {@code GET /shipments/{id}/timeline} lives on {@link ShipmentController}
 * instead, alongside the shipment it belongs to.
 */
@RestController
@RequestMapping("/api/v1/shipment-movement")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Shipment Movement", description = "Dispatch, In Scan, Out For Delivery, Deliver")
public class ShipmentMovementController {

    private final ShipmentService shipmentService;
    private final ManifestService manifestService;
    private final ShipmentMapper shipmentMapper;

    @PostMapping("/dispatch")
    @Operation(summary = "Dispatch a manifest",
            description = "Manifest must have at least one MANIFEST_CREATED shipment. Assigns the "
                    + "vehicle and driver, moves the manifest and every shipment on it to DISPATCHED.")
    public ApiResponse<DispatchManifestResponse> dispatch(@Valid @RequestBody DispatchManifestRequest request) {
        Manifest manifest = manifestService.dispatch(request.manifestId(), request.vehicleId(),
                request.driverUserId(), request.departureTime());
        // The shipments themselves are already DISPATCHED by the time this call returns —
        // count them by that status rather than MANIFEST_CREATED, which none of them are anymore.
        ShipmentCriteria dispatchedOnThisManifest = new ShipmentCriteria(
                Set.of(ShipmentStatus.DISPATCHED), null, null, null, null, manifest.getId(),
                null, null, null, null, null);
        int dispatchedCount = (int) shipmentService.search(dispatchedOnThisManifest, Pageable.unpaged())
                .getTotalElements();
        return ApiResponse.success(new DispatchManifestResponse(
                manifest.getId(), manifest.getManifestNumber(), manifest.getStatus(),
                manifest.getVehicleId(), manifest.getDriverUserId(), manifest.getDispatchedAt(),
                manifest.getDepartureTime(), dispatchedCount), "Manifest dispatched");
    }

    @PostMapping("/in-scan")
    @Operation(summary = "Receive shipments at the delivery branch",
            description = "Each tracking number must be DISPATCHED and its delivery branch must "
                    + "match the receiving branch. Bulk: per-item outcome. A non-empty "
                    + "missingTrackingNumbers auto-raises a Support ticket for the shortfall.")
    public ApiResponse<BulkMovementResponse> inScan(@Valid @RequestBody InScanRequest request) {
        var result = shipmentService.inScan(request.receivingBranchId(), request.trackingNumbers(),
                request.manifestNumber(), request.missingTrackingNumbers());
        return ApiResponse.success(shipmentMapper.toResponse(result));
    }

    @PostMapping("/out-for-delivery")
    @Operation(summary = "Assign shipments to a delivery user",
            description = "Each shipment must be IN_SCAN. Bulk: per-item outcome.")
    public ApiResponse<BulkMovementResponse> outForDelivery(@Valid @RequestBody OutForDeliveryRequest request) {
        var result = shipmentService.assignOutForDelivery(request.shipmentIds(), request.deliveryUserId());
        return ApiResponse.success(shipmentMapper.toResponse(result));
    }

    @PostMapping("/deliver")
    @Operation(summary = "Close a delivery",
            description = "Shipment must be OUT_FOR_DELIVERY. Captures receiver name (required), "
                    + "remarks, and the optional OTP/signature/photo.")
    public ApiResponse<ShipmentResponse> deliver(@Valid @RequestBody DeliverRequest request) {
        Shipment delivered = shipmentService.deliver(request.shipmentId(),
                new ShipmentService.DeliverCommand(request.receiverName(), request.remarks(),
                        request.otp(), request.signatureUrl(), request.photoUrl()));
        return ApiResponse.success(
                shipmentMapper.toResponse(delivered, shipmentService.getItems(delivered.getId()),
                        shipmentService.getDeliveryAssignment(delivered.getId()),
                        shipmentService.getAssets(delivered.getId()),
                        shipmentService.getEwayBill(delivered.getId()).orElse(null)),
                "Shipment delivered");
    }

    @GetMapping("/drs")
    @Operation(summary = "List DRS runs",
            description = "One row per delivery user + delivery branch + calendar day, grouped from "
                    + "DeliveryAssignment (there is no separate DRS/batch table). Defaults to the "
                    + "trailing 30 days when from/to are omitted. deliveryBranchId restricts to one "
                    + "branch server-side, e.g. a BRANCH_MANAGER's own branch.")
    public ApiResponse<java.util.List<com.courier.modules.shipment.api.dto.DrsSummaryResponse>> listDrs(
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @RequestParam(required = false) UUID deliveryBranchId) {
        return ApiResponse.success(shipmentService.listDrs(from, to, deliveryBranchId).stream()
                .map(shipmentMapper::toResponse).toList());
    }

    @GetMapping("/drs/detail")
    @Operation(summary = "One DRS run's shipments",
            description = "Every shipment assigned to this delivery user, at this delivery branch, "
                    + "on this day.")
    public ApiResponse<com.courier.modules.shipment.api.dto.DrsDetailResponse> drsDetail(
            @RequestParam UUID deliveryUserId, @RequestParam UUID deliveryBranchId,
            @RequestParam java.time.LocalDate runDate) {
        return ApiResponse.success(
                shipmentMapper.toResponse(shipmentService.getDrsDetail(deliveryUserId, deliveryBranchId, runDate)));
    }

    @PostMapping(value = "/{shipmentId}/pod-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a POD file",
            description = "Stores a delivery photo or signature capture (JPEG/PNG/WEBP/HEIC image, "
                    + "MP4/MOV video, or PDF) in the configured object store. Returns the URL to pass "
                    + "into deliver() as signatureUrl/photoUrl.")
    public ApiResponse<PodUploadResponse> uploadPodFile(@PathVariable UUID shipmentId,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam("kind") String kind) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded file could not be read. Please retry.");
        }
        String url = shipmentService.uploadPodFile(shipmentId, new ShipmentService.UploadPodFileCommand(
                content, file.getOriginalFilename(), file.getContentType(), kind));
        return ApiResponse.success(new PodUploadResponse(url), "File uploaded");
    }
}
