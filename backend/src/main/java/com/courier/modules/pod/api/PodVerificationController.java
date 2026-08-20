package com.courier.modules.pod.api;

import com.courier.modules.pod.api.dto.PodReviewRequest;
import com.courier.modules.pod.api.dto.PodVerificationResponse;
import com.courier.modules.pod.application.PodVerificationService;
import com.courier.modules.pod.domain.PodVerification;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentAsset;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.exception.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * POD Auto Verification — see {@code MEMORY/modules/pod-verification.md}. AI never updates a
 * shipment's status; {@code /shipment-movement/deliver} (unchanged) stays the only path to
 * {@code DELIVERED}.
 */
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "POD Auto Verification", description = "AI-scored proof of delivery: verify, read, manually review")
public class PodVerificationController {

    private final PodVerificationService podVerificationService;
    private final ShipmentService shipmentService;
    private final PodVerificationMapper mapper;

    @PostMapping(value = "/api/v1/shipments/{shipmentId}/pod/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a POD and run AI verification",
            description = "Shipment must be OUT_FOR_DELIVERY. Stores the photo (required) and "
                    + "signature (optional) via the existing document store, scores them, and "
                    + "returns PASS/REVIEW/FAIL. Never itself changes the shipment's status.")
    public ApiResponse<PodVerificationResponse> verify(
            @PathVariable UUID shipmentId,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(value = "signature", required = false) MultipartFile signature,
            @RequestParam("receiverName") String receiverName,
            @RequestParam(value = "awbNumber", required = false) String awbNumber,
            @RequestParam(value = "shipmentNumber", required = false) String shipmentNumberClaim,
            @RequestParam(value = "deliveryDateTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant deliveryDateTime) {

        var verification = podVerificationService.verify(shipmentId,
                new PodVerificationService.VerifyPodCommand(
                        readBytes(photo), originalFilename(photo), contentType(photo),
                        readBytes(signature), originalFilename(signature), contentType(signature),
                        receiverName, awbNumber, shipmentNumberClaim, deliveryDateTime));
        return ApiResponse.success(toResponse(verification), "POD verification complete");
    }

    @GetMapping("/api/v1/shipments/{shipmentId}/pod/verification")
    @Operation(summary = "Latest POD verification result for a shipment")
    public ApiResponse<PodVerificationResponse> getVerification(@PathVariable UUID shipmentId) {
        return ApiResponse.success(toResponse(podVerificationService.getLatest(shipmentId)));
    }

    @PostMapping("/api/v1/shipments/{shipmentId}/pod/review")
    @Operation(summary = "Approve or reject a REVIEW-status POD verification",
            description = "Only valid while the latest verification is REVIEW. Approve -> PASS, "
                    + "reject -> FAIL. Stamps the reviewer and timestamp.")
    public ApiResponse<PodVerificationResponse> review(@PathVariable UUID shipmentId,
                                                        @Valid @RequestBody PodReviewRequest request) {
        var verification = podVerificationService.review(shipmentId,
                new PodVerificationService.ReviewPodCommand(request.approve(), request.remarks()));
        return ApiResponse.success(toResponse(verification), request.approve() ? "POD approved" : "POD rejected");
    }

    @GetMapping("/api/v1/pod/pending-review")
    @Operation(summary = "Manual Review worklist",
            description = "Every POD verification currently REVIEW-status, oldest first — the "
                    + "POD Review screen's list.")
    public ApiResponse<List<PodVerificationResponse>> pendingReview() {
        return ApiResponse.success(podVerificationService.listPendingReview().stream()
                .map(this::toResponse).toList());
    }

    private PodVerificationResponse toResponse(PodVerification verification) {
        Shipment shipment;
        try {
            shipment = shipmentService.getById(verification.getShipmentId());
        } catch (RuntimeException e) {
            shipment = null;
        }
        List<ShipmentAsset> assets = shipmentService.getAssets(verification.getShipmentId());
        String photoUrl = assets.stream()
                .filter(a -> a.getId().equals(verification.getPodDocumentId()))
                .findFirst().map(ShipmentAsset::getAssetUrl).orElse(null);
        String signatureUrl = assets.stream()
                .filter(a -> "SIGNATURE".equals(a.getKind()))
                .findFirst().map(ShipmentAsset::getAssetUrl).orElse(null);
        return mapper.toResponse(verification, shipment, photoUrl, signatureUrl);
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded file could not be read. Please retry.");
        }
    }

    private static String originalFilename(MultipartFile file) {
        return file == null ? null : file.getOriginalFilename();
    }

    private static String contentType(MultipartFile file) {
        return file == null ? null : file.getContentType();
    }
}
