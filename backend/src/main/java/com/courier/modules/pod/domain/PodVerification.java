package com.courier.modules.pod.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * One AI verification run against a shipment's uploaded delivery photo/signature — see
 * {@code MEMORY/modules/pod-verification.md}. Written once per {@code POST .../pod/verify}
 * call (a re-upload after a {@link PodVerificationStatus#FAIL} creates a new row, it does
 * not overwrite the old one — the same "append, don't mutate" shape {@code ShipmentAsset}
 * already uses); {@code POST .../pod/review} then updates the same row's status/reviewer
 * fields in place, the one deliberate mutation this entity allows.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "pod_verification",
        indexes = @Index(name = "idx_pod_verification_shipment",
                columnList = "company_id, shipment_id, created_at"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PodVerification extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    /** {@code ShipmentAsset.id} of the primary (photo) document this run analysed — no
     *  physical FK, same cross-module-id convention this project uses throughout. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "pod_document_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID podDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private PodVerificationStatus verificationStatus;

    @Column(name = "verification_score", nullable = false)
    private int verificationScore;

    /** Newline-joined — see {@link #reasons()}/{@link #reasons(List)}, there is no list
     *  converter convention yet in this codebase so a delimiter is the honest choice over
     *  inventing one for a single column. */
    @Column(name = "verification_reasons", columnDefinition = "TEXT")
    private String verificationReasons;

    @Column(name = "detected_receiver_name", length = 255)
    private String detectedReceiverName;

    @Column(name = "detected_awb", length = 100)
    private String detectedAwb;

    @Column(name = "detected_date", length = 50)
    private String detectedDate;

    @Column(name = "signature_detected", nullable = false)
    private boolean signatureDetected;

    @Column(name = "image_quality", length = 20)
    private String imageQuality;

    /** SHA-256 hex of the uploaded photo bytes — duplicate-POD detection. */
    @Column(name = "pod_hash", length = 64)
    private String podHash;

    @Column(name = "ai_provider", nullable = false, length = 50)
    private String aiProvider;

    @Column(name = "ai_model", nullable = false, length = 50)
    private String aiModel;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "reviewed_by", columnDefinition = "BINARY(16)")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_remarks", length = 1000)
    private String reviewRemarks;

    public List<String> reasons() {
        if (verificationReasons == null || verificationReasons.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(Arrays.asList(verificationReasons.split("\n")));
    }

    public void reasons(List<String> reasons) {
        this.verificationReasons = reasons == null || reasons.isEmpty() ? null : String.join("\n", reasons);
    }

    public boolean isPendingReview() {
        return verificationStatus == PodVerificationStatus.REVIEW;
    }
}
