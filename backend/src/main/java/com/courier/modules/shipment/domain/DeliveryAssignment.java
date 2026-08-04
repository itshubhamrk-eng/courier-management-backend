package com.courier.modules.shipment.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
import java.util.UUID;

/**
 * The delivery desk's current assignment for one shipment, and — once closed — its proof
 * of delivery. One row per shipment (re-assigning before delivery updates it in place);
 * the append-only record of what happened is {@code shipment_status_history}, the same
 * "current state vs. ledger" split {@code finance.domain.Wallet}/{@code WalletTransaction}
 * already draws.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "delivery_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_assignment_company_shipment",
                columnNames = {"company_id", "shipment_id"}),
        indexes = {
                @Index(name = "idx_delivery_assignment_branch",
                        columnList = "company_id, delivery_branch_id, status"),
                @Index(name = "idx_delivery_assignment_user",
                        columnList = "company_id, delivery_user_id, status")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class DeliveryAssignment extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_branch_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID deliveryBranchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID deliveryUserId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DeliveryAssignmentStatus status = DeliveryAssignmentStatus.ASSIGNED;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "receiver_name", length = 150)
    private String receiverName;

    @Column(name = "delivery_remarks", length = 500)
    private String deliveryRemarks;

    @Column(name = "otp", length = 10)
    private String otp;

    @Column(name = "signature_url", length = 1000)
    private String signatureUrl;

    @Column(name = "photo_url", length = 1000)
    private String photoUrl;

    public void reassign(UUID deliveryUserId, UUID deliveryBranchId) {
        if (status == DeliveryAssignmentStatus.DELIVERED) {
            throw new BusinessRuleException("This shipment has already been delivered.");
        }
        this.deliveryUserId = deliveryUserId;
        this.deliveryBranchId = deliveryBranchId;
        this.assignedAt = Instant.now();
    }

    public void markDelivered(String receiverName, String remarks, String otp, String signatureUrl,
                              String photoUrl) {
        if (status == DeliveryAssignmentStatus.DELIVERED) {
            throw new BusinessRuleException("This shipment has already been delivered.");
        }
        if (receiverName == null || receiverName.isBlank()) {
            throw new BusinessRuleException("A receiver name is required to close a delivery.");
        }
        this.status = DeliveryAssignmentStatus.DELIVERED;
        this.deliveredAt = Instant.now();
        this.receiverName = receiverName.trim();
        this.deliveryRemarks = remarks == null || remarks.isBlank() ? null : remarks.trim();
        this.otp = otp == null || otp.isBlank() ? null : otp.trim();
        this.signatureUrl = signatureUrl == null || signatureUrl.isBlank() ? null : signatureUrl.trim();
        this.photoUrl = photoUrl == null || photoUrl.isBlank() ? null : photoUrl.trim();
    }
}
