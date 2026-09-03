package com.courier.modules.pod.application;

import com.courier.modules.pod.application.provider.PodAnalysisRequest;
import com.courier.modules.pod.application.provider.PodAnalysisResult;
import com.courier.modules.pod.application.provider.PodProviderUnavailableException;
import com.courier.modules.pod.application.provider.PodQrDecoder;
import com.courier.modules.pod.application.provider.PodVerificationProvider;
import com.courier.modules.pod.domain.PodVerification;
import com.courier.modules.pod.domain.PodVerificationRepository;
import com.courier.modules.pod.domain.PodVerificationStatus;
import com.courier.modules.shipment.application.ShipmentService;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentAsset;
import com.courier.modules.shipment.domain.ShipmentStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * See {@link PodVerificationService} and {@code MEMORY/modules/pod-verification.md}.
 *
 * <p>Deliberately does not log photo/signature bytes, POD URLs, or any detected-field value
 * — only ids, scores, and status, matching this module's own Privacy section. Every audit
 * entry carries the shipment number and the verification outcome, nothing image-derived.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PodVerificationServiceImpl implements PodVerificationService {

    private static final String ENTITY = "PodVerification";

    private static final String WRITERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "', '" + Roles.OPERATOR + "')";
    private static final String REVIEWERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "')";
    private static final String READERS = "isAuthenticated()";

    private final PodVerificationRepository podVerificationRepository;
    private final ShipmentService shipmentService;
    private final PodVerificationProvider provider;
    private final PodVerificationProperties properties;
    private final AuditService auditService;

    @Override
    @Transactional
    @PreAuthorize(WRITERS)
    public PodVerification verify(UUID shipmentId, VerifyPodCommand command) {
        UUID companyId = requireCompany();
        Shipment shipment = shipmentService.getById(shipmentId);

        if (shipment.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new BusinessRuleException("Shipment %s is %s — POD verification only applies to "
                    .formatted(shipment.getShipmentNumber(), shipment.getStatus())
                    + "an OUT_FOR_DELIVERY shipment.");
        }
        if (command.photoContent() == null || command.photoContent().length == 0) {
            throw new BusinessRuleException("A delivery photo is required to run POD verification.");
        }

        String podHash = sha256Hex(command.photoContent());
        boolean duplicateSuspected = !podVerificationRepository
                .findDuplicatesWithinCompany(companyId, podHash, shipmentId).isEmpty();

        // A live camera scan (delivery app's own QR reader, before this upload) wins when
        // present — it's the freshest read off the physical label. Falling back to decoding
        // the already-uploaded photo itself means the cross-check still runs even when the
        // delivery app has no live-scan step (or the operator's device lacks camera access),
        // at the cost of depending on the label happening to be visible in the photo.
        String qrScanValue = command.qrScanValue() != null && !command.qrScanValue().isBlank()
                ? command.qrScanValue().trim()
                : PodQrDecoder.decode(command.photoContent());

        PodAnalysisResult result;
        boolean providerAvailable = true;
        try {
            result = provider.analyze(new PodAnalysisRequest(
                    command.photoContent(), command.photoContentType(),
                    command.signatureContent(),
                    command.receiverName(), command.awbNumber(), command.shipmentNumberClaim(),
                    shipment.getTrackingNumber(), shipment.getShipmentNumber(),
                    command.deliveryDateTime() == null ? Instant.now() : command.deliveryDateTime(),
                    duplicateSuspected, qrScanValue));
        } catch (PodProviderUnavailableException e) {
            log.warn("POD AI provider unavailable for shipment {} — routing to manual review",
                    shipment.getShipmentNumber());
            providerAvailable = false;
            result = new PodAnalysisResult(properties.getManualReviewThreshold(),
                    List.of("AI provider unavailable — routed to manual review."),
                    command.signatureContent() != null && command.signatureContent().length > 0,
                    null, command.receiverName(), command.awbNumber(), null, false, true);
        }

        // Uploaded to the existing object-store seam (ShipmentService.uploadPodFile ->
        // FileStoragePort, the same one POD capture has always used) and persisted
        // immediately regardless of outcome — even a FAIL needs a durable record of what
        // was submitted, for the eventual manual re-review / audit trail.
        String photoUrl = shipmentService.uploadPodFile(shipmentId, new ShipmentService.UploadPodFileCommand(
                command.photoContent(), command.photoFilename(), command.photoContentType(), "PHOTO"));
        ShipmentAsset photoAsset = shipmentService.attachPodAsset(shipmentId, "PHOTO", photoUrl);
        if (command.signatureContent() != null && command.signatureContent().length > 0) {
            String signatureUrl = shipmentService.uploadPodFile(shipmentId,
                    new ShipmentService.UploadPodFileCommand(command.signatureContent(),
                            command.signatureFilename(), command.signatureContentType(), "SIGNATURE"));
            shipmentService.attachPodAsset(shipmentId, "SIGNATURE", signatureUrl);
        }

        PodVerificationStatus status = resolveStatus(result);

        PodVerification verification = PodVerification.builder()
                .shipmentId(shipmentId)
                .podDocumentId(photoAsset.getId())
                .verificationStatus(status)
                .verificationScore(result.score())
                .detectedReceiverName(result.detectedReceiverName())
                .detectedAwb(result.detectedAwb())
                .detectedDate(result.detectedDate())
                .signatureDetected(result.signatureDetected())
                .imageQuality(result.imageQuality())
                .podHash(podHash)
                .aiProvider(providerAvailable ? provider.providerName() : "unavailable")
                .aiModel(providerAvailable ? provider.modelName() : "n/a")
                .verifiedAt(Instant.now())
                .build();
        verification.reasons(result.reasons());
        PodVerification saved = podVerificationRepository.save(verification);

        auditService.record(AuditAction.POD_VERIFICATION_RUN, ENTITY, saved.getId(),
                Map.of("shipmentNumber", shipment.getShipmentNumber(),
                        "status", status.name(), "score", result.score()));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public PodVerification getLatest(UUID shipmentId) {
        UUID companyId = requireCompany();
        // Confirms the shipment itself is within the caller's company before reading its
        // verification — 404s the same way a direct shipment lookup would.
        shipmentService.getById(shipmentId);
        return podVerificationRepository.findLatestByShipmentIdWithinCompany(shipmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, shipmentId));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(REVIEWERS)
    public List<PodVerification> listPendingReview() {
        return podVerificationRepository.findAllPendingReviewWithinCompany(requireCompany());
    }

    @Override
    @Transactional
    @PreAuthorize(REVIEWERS)
    public PodVerification review(UUID shipmentId, ReviewPodCommand command) {
        UUID companyId = requireCompany();
        shipmentService.getById(shipmentId);
        PodVerification verification = podVerificationRepository
                .findLatestByShipmentIdWithinCompany(shipmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, shipmentId));

        if (!verification.isPendingReview()) {
            throw new BusinessRuleException("This POD verification is %s — only a REVIEW result "
                    .formatted(verification.getVerificationStatus())
                    + "can be approved or rejected.");
        }

        UUID actorId = SecurityUtils.getCurrentUserId().orElse(null);
        verification.setVerificationStatus(
                command.approve() ? PodVerificationStatus.PASS : PodVerificationStatus.FAIL);
        verification.setReviewedBy(actorId);
        verification.setReviewedAt(Instant.now());
        verification.setReviewRemarks(command.remarks());
        PodVerification saved = podVerificationRepository.save(verification);

        auditService.record(
                command.approve() ? AuditAction.POD_VERIFICATION_APPROVED : AuditAction.POD_VERIFICATION_REJECTED,
                ENTITY, saved.getId(), Map.of("shipmentId", shipmentId.toString()));

        return saved;
    }

    private PodVerificationStatus resolveStatus(PodAnalysisResult result) {
        if (result.mustReviewRegardlessOfScore() && result.score() >= properties.getAutoVerifyThreshold()) {
            return PodVerificationStatus.REVIEW;
        }
        if (result.score() >= properties.getAutoVerifyThreshold()) {
            return PodVerificationStatus.PASS;
        }
        if (result.score() >= properties.getManualReviewThreshold()) {
            return PodVerificationStatus.REVIEW;
        }
        return PodVerificationStatus.FAIL;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request."));
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
