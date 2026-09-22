package com.courier.modules.pod.api;

import com.courier.modules.pod.api.dto.BulkPodUploadRowResponse;
import com.courier.modules.pod.api.dto.DeliveredShipmentPodResponse;
import com.courier.modules.pod.api.dto.PodReviewRequest;
import com.courier.modules.pod.api.dto.PodVerificationResponse;
import com.courier.modules.pod.application.PodVerificationService;
import com.courier.modules.pod.domain.PodEntryStatus;
import com.courier.modules.pod.domain.PodVerification;
import com.courier.modules.shipment.api.ShipmentMapper;
import com.courier.modules.shipment.api.dto.ShipmentSearchRequest;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentAsset;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

    private static final Map<String, String> SORTABLE = Map.of(
            "shipmentNumber", "shipmentNumber",
            "trackingNumber", "trackingNumber",
            "bookingDate", "bookingDate",
            "createdDate", "createdAt",
            "createdAt", "createdAt");

    private static final int MAX_PAGE_SIZE = 100;

    private final PodVerificationService podVerificationService;
    private final ShipmentService shipmentService;
    private final PodVerificationMapper mapper;
    private final ShipmentMapper shipmentMapper;

    @PostMapping(value = "/api/v1/shipments/{shipmentId}/pod/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a POD and run AI scoring",
            description = "Shipment must be OUT_FOR_DELIVERY. Stores the photo (required) and "
                    + "signature (optional) via the existing document store, scores them for "
                    + "reference, and always returns PENDING — a human always makes the "
                    + "PASS/FAIL call via the review endpoint. Never itself changes the "
                    + "shipment's status.")
    public ApiResponse<PodVerificationResponse> verify(
            @PathVariable UUID shipmentId,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(value = "signature", required = false) MultipartFile signature,
            @RequestParam("receiverName") String receiverName,
            @RequestParam(value = "awbNumber", required = false) String awbNumber,
            @RequestParam(value = "shipmentNumber", required = false) String shipmentNumberClaim,
            @RequestParam(value = "deliveryDateTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant deliveryDateTime,
            @RequestParam(value = "qrScanValue", required = false) String qrScanValue) {

        var verification = podVerificationService.verify(shipmentId,
                new PodVerificationService.VerifyPodCommand(
                        readBytes(photo), originalFilename(photo), contentType(photo),
                        readBytes(signature), originalFilename(signature), contentType(signature),
                        receiverName, awbNumber, shipmentNumberClaim, deliveryDateTime, qrScanValue));
        return ApiResponse.success(toResponse(verification), "POD verification complete");
    }

    @GetMapping("/api/v1/shipments/{shipmentId}/pod/verification")
    @Operation(summary = "Latest POD verification result for a shipment")
    public ApiResponse<PodVerificationResponse> getVerification(@PathVariable UUID shipmentId) {
        return ApiResponse.success(toResponse(podVerificationService.getLatest(shipmentId)));
    }

    @PostMapping("/api/v1/shipments/{shipmentId}/pod/review")
    @Operation(summary = "Approve or reject a PENDING POD verification",
            description = "Only valid while the latest verification is PENDING. Approve -> PASS, "
                    + "reject -> FAIL. Stamps the reviewer and timestamp.")
    public ApiResponse<PodVerificationResponse> review(@PathVariable UUID shipmentId,
                                                        @Valid @RequestBody PodReviewRequest request) {
        var verification = podVerificationService.review(shipmentId,
                new PodVerificationService.ReviewPodCommand(request.approve(), request.remarks()));
        return ApiResponse.success(toResponse(verification), request.approve() ? "POD approved" : "POD rejected");
    }

    @PostMapping(value = "/api/v1/pod/company-upload/{shipmentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Company-level POD upload — no branch login required",
            description = "COMPANY_ADMIN only. Uploads a POD for any of the company's own "
                    + "OUT_FOR_DELIVERY/DELIVERED shipments, independent of branch context, "
                    + "and always auto-approves it — no AI call, no PENDING step.")
    public ApiResponse<PodVerificationResponse> uploadByCompany(
            @PathVariable UUID shipmentId,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam(value = "signature", required = false) MultipartFile signature,
            @RequestParam("receiverName") String receiverName,
            @RequestParam(value = "deliveryDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam(value = "deliveredBy", required = false) String deliveredBy,
            @RequestParam(value = "podDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate podDate,
            @RequestParam(value = "podTime", required = false)
            @DateTimeFormat(pattern = "HH:mm") LocalTime podTime,
            @RequestParam(value = "status", required = false) PodEntryStatus status,
            @RequestParam(value = "remark", required = false) String remark) {

        var verification = podVerificationService.uploadByCompany(shipmentId,
                new PodVerificationService.CompanyUploadPodCommand(
                        readBytes(photo), originalFilename(photo), contentType(photo),
                        readBytes(signature), originalFilename(signature), contentType(signature),
                        receiverName, deliveryDate, deliveredBy, podDate, podTime, status, remark));
        return ApiResponse.success(toResponse(verification), "POD uploaded and approved");
    }

    @PostMapping(value = "/api/v1/pod/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk POD Upload — auto-detect, match and score many PODs at once",
            description = "COMPANY_ADMIN only. Up to 50 scanned/collected POD photos in one "
                    + "call, no shipment picked in advance: each photo is read by the AI "
                    + "provider for real (the shipment/AWB number actually printed or "
                    + "written on it, not a structural-only check), matched against this "
                    + "company's own shipments, and — on exactly one match against an "
                    + "OUT_FOR_DELIVERY/DELIVERED shipment — scored and stored PENDING, same "
                    + "as the single-shipment verify endpoint. A human still approves/rejects "
                    + "via the review endpoint; a photo that can't be read or matches none/"
                    + "more than one shipment is reported back unmatched, never guessed at.")
    public ApiResponse<List<BulkPodUploadRowResponse>> bulkUpload(
            @RequestParam("photos") List<MultipartFile> photos) {
        var items = photos.stream()
                .map(f -> new PodVerificationService.BulkUploadPodItem(
                        readBytes(f), originalFilename(f), contentType(f)))
                .toList();
        var outcomes = podVerificationService.bulkUpload(items);
        List<BulkPodUploadRowResponse> rows = outcomes.stream()
                .map(outcome -> mapper.toBulkRow(outcome,
                        outcome.verification() == null ? null : toResponse(outcome.verification())))
                .toList();
        long matched = rows.stream().filter(r -> "MATCHED".equals(r.matchStatus())).count();
        return ApiResponse.success(rows, "%d of %d matched and queued for review".formatted(matched, rows.size()));
    }

    @GetMapping("/api/v1/pod/pending-review")
    @Operation(summary = "Manual Review worklist",
            description = "Every POD verification currently PENDING, oldest first — the "
                    + "POD Review screen's list.")
    public ApiResponse<List<PodVerificationResponse>> pendingReview() {
        return ApiResponse.success(podVerificationService.listPendingReview().stream()
                .map(this::toResponse).toList());
    }

    @GetMapping("/api/v1/pod/delivered")
    @Operation(summary = "POD Review — every delivered shipment, with its latest POD verification if any",
            description = "Same filters as GET /shipments (branch, date range, search) — "
                    + "status is always forced to DELIVERED regardless of what's passed. "
                    + "Every delivered shipment appears exactly once, whether or not POD Auto "
                    + "Verification ever ran against it.")
    public ApiResponse<PageResponse<DeliveredShipmentPodResponse>> delivered(
            @Valid @ParameterObject ShipmentSearchRequest search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        ShipmentCriteria base = shipmentMapper.toCriteria(search);
        ShipmentCriteria criteria = new ShipmentCriteria(Set.of(ShipmentStatus.DELIVERED),
                base.bookingBranchId(), base.deliveryBranchId(), base.currentLocationId(), base.nextLocationId(),
                base.manifestId(), base.bookingDateFrom(), base.bookingDateTo(),
                base.deliveredDateFrom(), base.deliveredDateTo(), base.paymentModeId(), base.search(),
                base.toCity(), base.unassignedDeliveryBranch());

        Page<Shipment> page = shipmentService.search(criteria, sanitise(pageable));
        List<UUID> ids = page.getContent().stream().map(Shipment::getId).toList();
        Map<UUID, PodVerification> verifications = podVerificationService.latestByShipmentIds(ids);
        Map<UUID, List<ShipmentAsset>> podAssets = shipmentService.podAssetsFor(ids);
        Map<UUID, Instant> deliveredAt = shipmentService.deliveredAtFor(ids);
        Map<UUID, Instant> receivedAt = shipmentService.receivedAtFor(ids);

        return ApiResponse.success(PageResponse.from(page, s -> mapper.toDeliveredRow(
                s, verifications.get(s.getId()), receivedAt.get(s.getId()), deliveredAt.get(s.getId()),
                podAssets.getOrDefault(s.getId(), List.of()))));
    }

    private Pageable sanitise(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        List<Sort.Order> orders = pageable.getSort().stream()
                .map(order -> {
                    String property = SORTABLE.get(order.getProperty());
                    if (property == null) {
                        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                                "Cannot sort by '%s'. Allowed: %s"
                                        .formatted(order.getProperty(),
                                                String.join(", ", new TreeSet<>(SORTABLE.keySet()))));
                    }
                    return new Sort.Order(order.getDirection(), property);
                })
                .toList();
        Sort sort = orders.isEmpty() ? Sort.by(Sort.Order.desc("createdAt")) : Sort.by(orders);
        return org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), size, sort);
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
