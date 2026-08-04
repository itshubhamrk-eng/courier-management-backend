package com.courier.modules.shipment.api;

import com.courier.modules.manifest.application.ManifestService;
import com.courier.modules.manifest.domain.Manifest;
import com.courier.modules.shipment.api.dto.BulkMovementResponse;
import com.courier.modules.shipment.api.dto.DeliverRequest;
import com.courier.modules.shipment.api.dto.DispatchManifestRequest;
import com.courier.modules.shipment.api.dto.DispatchManifestResponse;
import com.courier.modules.shipment.api.dto.InScanRequest;
import com.courier.modules.shipment.api.dto.OutForDeliveryRequest;
import com.courier.modules.shipment.api.dto.ShipmentResponse;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * The four remaining movement steps, in business-flow order: Dispatch -&gt; In Scan -&gt;
 * Out For Delivery -&gt; Deliver. Out Scan is gone as its own step (V20, on direct
 * request) — adding a shipment to a manifest already is "out scan created", so Dispatch
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
@Tag(name = "Shipment Movement", description = "Out Scan, Dispatch, In Scan, Out For Delivery, Deliver")
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
                request.driverUserId());
        // The shipments themselves are already DISPATCHED by the time this call returns —
        // count them by that status rather than MANIFEST_CREATED, which none of them are anymore.
        ShipmentCriteria dispatchedOnThisManifest = new ShipmentCriteria(
                Set.of(ShipmentStatus.DISPATCHED), null, null, manifest.getId(), null, null, null);
        int dispatchedCount = (int) shipmentService.search(dispatchedOnThisManifest, Pageable.unpaged())
                .getTotalElements();
        return ApiResponse.success(new DispatchManifestResponse(
                manifest.getId(), manifest.getManifestNumber(), manifest.getStatus(),
                manifest.getVehicleId(), manifest.getDriverUserId(), manifest.getDispatchedAt(),
                dispatchedCount), "Manifest dispatched");
    }

    @PostMapping("/in-scan")
    @Operation(summary = "Receive shipments at the delivery branch",
            description = "Each tracking number must be DISPATCHED and its delivery branch must "
                    + "match the receiving branch. Bulk: per-item outcome.")
    public ApiResponse<BulkMovementResponse> inScan(@Valid @RequestBody InScanRequest request) {
        var result = shipmentService.inScan(request.receivingBranchId(), request.trackingNumbers());
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
                shipmentMapper.toResponse(delivered, shipmentService.getItems(delivered.getId())),
                "Shipment delivered");
    }
}
