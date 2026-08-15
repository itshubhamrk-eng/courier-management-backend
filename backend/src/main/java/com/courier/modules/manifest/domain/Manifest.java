package com.courier.modules.manifest.domain;

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
 * Groups shipments travelling one booking-branch -&gt; delivery-branch run. Built as the
 * minimal prerequisite Shipment Movement needs underneath it (see V19's migration
 * comment) — enough to create a manifest, attach shipments to it, and assign a vehicle
 * and driver at dispatch time. No physical FK to booking/delivery branch, vehicle or
 * driver (a company user) — every one of them belongs to a different module, validated
 * in the service layer, the same cross-module treatment every id on {@code Shipment}
 * already gets.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "manifests",
        uniqueConstraints = @UniqueConstraint(name = "uk_manifests_company_number",
                columnNames = {"company_id", "manifest_number"}),
        indexes = {
                @Index(name = "idx_manifests_company_status", columnList = "company_id, status, created_at"),
                @Index(name = "idx_manifests_booking_branch",
                        columnList = "company_id, booking_branch_id, status")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Manifest extends CompanyOwnedEntity {

    @Column(name = "manifest_number", nullable = false, updatable = false, length = 30)
    private String manifestNumber;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "booking_branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID bookingBranchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID deliveryBranchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "vehicle_id", columnDefinition = "BINARY(16)")
    private UUID vehicleId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "driver_user_id", columnDefinition = "BINARY(16)")
    private UUID driverUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ManifestStatus status = ManifestStatus.CREATED;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    /** Operator-entered departure time — distinct from {@code dispatchedAt} (the server
     *  clock at the moment THC was created); this is when the vehicle actually leaves,
     *  which the operator may back- or forward-date relative to that. Optional: falls
     *  back to {@code dispatchedAt} wherever it is displayed. */
    @Column(name = "departure_time")
    private Instant departureTime;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "remarks", length = 500)
    private String remarks;

    public boolean isDispatched() {
        return status != ManifestStatus.CREATED;
    }

    /**
     * @throws BusinessRuleException already dispatched — a manifest's vehicle/driver are
     *         fixed at the moment it leaves, the same "point of no return" DISPATCH
     *         carries everywhere else in this project
     */
    public void dispatch(UUID vehicleId, UUID driverUserId, Instant departureTime) {
        if (isDispatched()) {
            throw new BusinessRuleException(
                    "Manifest %s has already been dispatched.".formatted(manifestNumber));
        }
        this.vehicleId = vehicleId;
        this.driverUserId = driverUserId;
        this.status = ManifestStatus.DISPATCHED;
        this.dispatchedAt = Instant.now();
        this.departureTime = departureTime != null ? departureTime : this.dispatchedAt;
    }
}
