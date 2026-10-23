package com.courier.modules.shipment.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One failed delivery attempt (V135). Append-only. */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "delivery_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_attempts_shipment_no",
                columnNames = {"company_id", "shipment_id", "attempt_no"}),
        indexes = @Index(name = "idx_delivery_attempts_attempted", columnList = "company_id, attempted_at"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class DeliveryAttempt extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30, updatable = false)
    private DeliveryFailureReason reason;

    @Column(name = "remarks", length = 500, updatable = false)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20, updatable = false)
    private DeliveryAttemptOutcome outcome;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_user_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID deliveryUserId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_branch_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID deliveryBranchId;

    @Column(name = "drs_number", length = 20, updatable = false)
    private String drsNumber;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;
}
