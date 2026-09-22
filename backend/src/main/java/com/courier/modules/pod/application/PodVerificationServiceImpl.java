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
    /** Approve/reject — and the company-direct upload override — are company-level
     *  decisions, narrower than {@link #WRITERS}: a delivery operator or branch manager may
     *  capture/run a POD but not decide their own branch's submission, on direct user
     *  request ("approve or reject access should be company level"). Narrowed from also
     *  including {@code BRANCH_MANAGER} (this module's original build) once approval started
     *  crediting delivery commission. */
    private static final String REVIEWERS = "hasRole('" + Roles.COMPANY_ADMIN + "')";
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
                    null, command.receiverName(), command.awbNumber(), null, false, true,
                    false, command.shipmentNumberClaim());
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

        PodVerification verification = PodVerification.builder()
                .shipmentId(shipmentId)
                .podDocumentId(photoAsset.getId())
                // Always PENDING — the AI score/reasons below are informational only, a
                // human always makes the PASS/FAIL call via review(). See
                // PodVerificationStatus's own javadoc.
                .verificationStatus(PodVerificationStatus.PENDING)
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
                        "status", PodVerificationStatus.PENDING.name(), "score", result.score()));

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
    @Transactional(readOnly = true)
    @PreAuthorize(READERS)
    public Map<UUID, PodVerification> latestByShipmentIds(java.util.Collection<UUID> shipmentIds) {
        if (shipmentIds.isEmpty()) return Map.of();
        // Newest-first from the repository query, so the first one kept per shipmentId in
        // this merge (a,b) -> a is the latest — same idiom findLatestByShipmentIdWithinCompany
        // already uses for a single shipment.
        return podVerificationRepository.findAllByShipmentIdInWithinCompany(shipmentIds, requireCompany())
                .stream()
                .collect(java.util.stream.Collectors.toMap(PodVerification::getShipmentId, v -> v, (a, b) -> a));
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
            throw new BusinessRuleException("This POD verification is %s — only a PENDING result "
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

        if (command.approve()) {
            shipmentService.markPodApproved(shipmentId);
        }

        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(REVIEWERS)
    public PodVerification uploadByCompany(UUID shipmentId, CompanyUploadPodCommand command) {
        UUID companyId = requireCompany();
        Shipment shipment = shipmentService.getById(shipmentId);

        if (shipment.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY && shipment.getStatus() != ShipmentStatus.DELIVERED) {
            throw new BusinessRuleException("Shipment %s is %s — a company POD upload only applies to "
                    .formatted(shipment.getShipmentNumber(), shipment.getStatus())
                    + "an OUT_FOR_DELIVERY or DELIVERED shipment.");
        }
        if (command.photoContent() == null || command.photoContent().length == 0) {
            throw new BusinessRuleException("A delivery photo is required to upload a POD.");
        }

        String podHash = sha256Hex(command.photoContent());

        String photoUrl = shipmentService.uploadPodFile(shipmentId, new ShipmentService.UploadPodFileCommand(
                command.photoContent(), command.photoFilename(), command.photoContentType(), "PHOTO"));
        ShipmentAsset photoAsset = shipmentService.attachPodAsset(shipmentId, "PHOTO", photoUrl);
        if (command.signatureContent() != null && command.signatureContent().length > 0) {
            String signatureUrl = shipmentService.uploadPodFile(shipmentId,
                    new ShipmentService.UploadPodFileCommand(command.signatureContent(),
                            command.signatureFilename(), command.signatureContentType(), "SIGNATURE"));
            shipmentService.attachPodAsset(shipmentId, "SIGNATURE", signatureUrl);
        }

        // A company-direct upload is always auto-approved, on direct user request ("if
        // uploaded by company then it should be direct approved") — no AI call, no PENDING
        // step. Skips the AI provider entirely rather than routing through it only to
        // override its result, since the whole point is the company vouching for it directly.
        PodVerification verification = PodVerification.builder()
                .shipmentId(shipmentId)
                .podDocumentId(photoAsset.getId())
                .verificationStatus(PodVerificationStatus.PASS)
                .verificationScore(100)
                .signatureDetected(command.signatureContent() != null && command.signatureContent().length > 0)
                .detectedReceiverName(command.receiverName())
                .deliveryDate(command.deliveryDate())
                .deliveredBy(command.deliveredBy())
                .podDate(command.podDate())
                .podTime(command.podTime())
                .entryStatus(command.entryStatus())
                .remark(command.remark())
                .podHash(podHash)
                .aiProvider("company-direct")
                .aiModel("n/a")
                .verifiedAt(Instant.now())
                .reviewedBy(SecurityUtils.getCurrentUserId().orElse(null))
                .reviewedAt(Instant.now())
                .reviewRemarks("Uploaded and auto-approved by company")
                .build();
        verification.reasons(List.of("Uploaded directly by company — auto-approved, no AI review."));
        PodVerification saved = podVerificationRepository.save(verification);

        auditService.record(AuditAction.POD_VERIFICATION_COMPANY_UPLOAD, ENTITY, saved.getId(),
                Map.of("shipmentNumber", shipment.getShipmentNumber()));

        shipmentService.markPodApproved(shipmentId);

        return saved;
    }

    private static final int MAX_BULK_ITEMS = 50;

    @Override
    @Transactional
    @PreAuthorize(REVIEWERS)
    public List<PodVerificationService.BulkUploadPodOutcome> bulkUpload(
            List<PodVerificationService.BulkUploadPodItem> items) {
        if (items.isEmpty()) {
            throw new BusinessRuleException("Upload at least one POD photo.");
        }
        if (items.size() > MAX_BULK_ITEMS) {
            throw new BusinessRuleException(
                    "At most %d files per bulk upload — split this into smaller batches."
                            .formatted(MAX_BULK_ITEMS));
        }
        UUID companyId = requireCompany();

        return items.stream().map(item -> bulkUploadOne(companyId, item)).toList();
    }

    /** One file of {@link #bulkUpload}: reads the shipment number off the photo itself (real
     *  AI content analysis, no shipment context yet), matches it against this company's own
     *  shipments, and on exactly one match, persists a fresh PENDING verification exactly like
     *  {@link #verify} — same downstream review flow, only the shipment was found automatically
     *  instead of picked by a human first. */
    private PodVerificationService.BulkUploadPodOutcome bulkUploadOne(
            UUID companyId, PodVerificationService.BulkUploadPodItem item) {
        if (item.photoContent() == null || item.photoContent().length == 0) {
            return errorOutcome(item.photoFilename(), "File is empty or could not be read.");
        }

        String podHash = sha256Hex(item.photoContent());
        boolean duplicateSuspected = !podVerificationRepository.findByHashWithinCompany(companyId, podHash).isEmpty();
        String qrScanValue = PodQrDecoder.decode(item.photoContent());

        PodAnalysisResult result;
        boolean providerAvailable = true;
        try {
            // No shipment picked yet — claimed/actual AWB+number are both unknown at this
            // point, so PodGroundTruthRules' mismatch check is a no-op here; matching happens
            // below, off what the provider actually read out of the pixels.
            result = provider.analyze(new PodAnalysisRequest(
                    item.photoContent(), item.photoContentType(), null, null,
                    null, null, null, null,
                    Instant.now(), duplicateSuspected, qrScanValue));
        } catch (PodProviderUnavailableException e) {
            providerAvailable = false;
            result = null;
        }
        if (!providerAvailable) {
            return errorOutcome(item.photoFilename(),
                    "AI provider unavailable — cannot auto-detect a shipment number for bulk upload.");
        }

        List<String> candidates = new java.util.LinkedHashSet<>(List.of(
                nonBlankOrEmpty(result.detectedShipmentNumber()),
                nonBlankOrEmpty(result.detectedAwb()),
                nonBlankOrEmpty(qrScanValue)))
                .stream().filter(s -> !s.isEmpty()).toList();

        if (candidates.isEmpty()) {
            return new PodVerificationService.BulkUploadPodOutcome(item.photoFilename(),
                    PodVerificationService.BulkUploadPodMatchStatus.NO_MATCH,
                    "Could not read a shipment/AWB number off this image, and no QR code was decodable.",
                    null, null, null, null, null, null);
        }

        List<Shipment> matches = shipmentService.bulkTrack(candidates).stream()
                .collect(java.util.stream.Collectors.toMap(Shipment::getId, s -> s, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream().toList();

        if (matches.isEmpty()) {
            return new PodVerificationService.BulkUploadPodOutcome(item.photoFilename(),
                    PodVerificationService.BulkUploadPodMatchStatus.NO_MATCH,
                    "No shipment in this company matches \"%s\".".formatted(String.join("\", \"", candidates)),
                    result.detectedShipmentNumber(), result.detectedAwb(), null, null, null, null);
        }
        if (matches.size() > 1) {
            return new PodVerificationService.BulkUploadPodOutcome(item.photoFilename(),
                    PodVerificationService.BulkUploadPodMatchStatus.AMBIGUOUS,
                    "Matches more than one shipment (%s) — resolve manually."
                            .formatted(matches.stream().map(Shipment::getShipmentNumber)
                                    .collect(java.util.stream.Collectors.joining(", "))),
                    result.detectedShipmentNumber(), result.detectedAwb(), null, null, null, null);
        }

        Shipment shipment = matches.get(0);
        if (shipment.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY && shipment.getStatus() != ShipmentStatus.DELIVERED) {
            return new PodVerificationService.BulkUploadPodOutcome(item.photoFilename(),
                    PodVerificationService.BulkUploadPodMatchStatus.INVALID_STATUS,
                    "Matched %s, but it is %s — bulk upload only applies to an OUT_FOR_DELIVERY or DELIVERED shipment."
                            .formatted(shipment.getShipmentNumber(), shipment.getStatus()),
                    result.detectedShipmentNumber(), result.detectedAwb(),
                    shipment.getId(), shipment.getShipmentNumber(), shipment.getTrackingNumber(), null);
        }

        String photoUrl = shipmentService.uploadPodFile(shipment.getId(), new ShipmentService.UploadPodFileCommand(
                item.photoContent(), item.photoFilename(), item.photoContentType(), "PHOTO"));
        ShipmentAsset photoAsset = shipmentService.attachPodAsset(shipment.getId(), "PHOTO", photoUrl);

        PodVerification verification = PodVerification.builder()
                .shipmentId(shipment.getId())
                .podDocumentId(photoAsset.getId())
                .verificationStatus(PodVerificationStatus.PENDING)
                .verificationScore(result.score())
                .detectedReceiverName(result.detectedReceiverName())
                .detectedAwb(result.detectedAwb())
                .detectedShipmentNumber(result.detectedShipmentNumber())
                .detectedDate(result.detectedDate())
                .signatureDetected(result.signatureDetected())
                .stampDetected(result.stampDetected())
                .imageQuality(result.imageQuality())
                .podHash(podHash)
                .aiProvider(provider.providerName())
                .aiModel(provider.modelName())
                .verifiedAt(Instant.now())
                .build();
        verification.reasons(result.reasons());
        PodVerification saved = podVerificationRepository.save(verification);

        auditService.record(AuditAction.POD_VERIFICATION_RUN, ENTITY, saved.getId(),
                Map.of("shipmentNumber", shipment.getShipmentNumber(), "status", PodVerificationStatus.PENDING.name(),
                        "score", result.score(), "source", "bulk-upload", "filename",
                        item.photoFilename() == null ? "" : item.photoFilename()));

        return new PodVerificationService.BulkUploadPodOutcome(item.photoFilename(),
                PodVerificationService.BulkUploadPodMatchStatus.MATCHED,
                "Matched %s — PENDING, awaiting review.".formatted(shipment.getShipmentNumber()),
                result.detectedShipmentNumber(), result.detectedAwb(),
                shipment.getId(), shipment.getShipmentNumber(), shipment.getTrackingNumber(), saved);
    }

    private static PodVerificationService.BulkUploadPodOutcome errorOutcome(String filename, String message) {
        return new PodVerificationService.BulkUploadPodOutcome(filename,
                PodVerificationService.BulkUploadPodMatchStatus.ERROR, message,
                null, null, null, null, null, null);
    }

    private static String nonBlankOrEmpty(String s) {
        return s == null ? "" : s.trim();
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
