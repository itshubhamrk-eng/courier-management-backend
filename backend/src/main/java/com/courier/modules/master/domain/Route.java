package com.courier.modules.master.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A lane between two branches: booking branch -> delivery branch, with a distance and a
 * transit promise.
 *
 * <p>Shipment Booking, the Rate Master and Manifest Planning all read this, which is why
 * it is a first-class master rather than three copies of the same two branch ids.
 *
 * <p><b>Direction matters.</b> Pune -> Mumbai and Mumbai -> Pune are two rows. The
 * kilometres are usually the same; the transit days frequently are not, because the
 * outbound leaves on the night line-haul and the return does not.
 *
 * <p>The branch ids are plain {@code UUID} columns with no foreign key, and Master never
 * imports a {@code Branch}: the ids are validated through {@link BranchLookupPort}, this
 * module's own seam onto {@code modules/company}. Same arrangement Finance uses for
 * wallets.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_routes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_routes_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_routes_pair",
                        columnNames = {"company_id", "booking_branch_id", "delivery_branch_id"})
        },
        indexes = {
                @Index(name = "idx_master_routes_booking",
                        columnList = "company_id, booking_branch_id"),
                @Index(name = "idx_master_routes_delivery",
                        columnList = "company_id, delivery_branch_id"),
                @Index(name = "idx_master_routes_status", columnList = "company_id, status")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Route extends MasterDataEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "booking_branch_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID bookingBranchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "delivery_branch_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID deliveryBranchId;

    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "distance_unit", nullable = false, length = 10)
    private DistanceUnit distanceUnit = DistanceUnit.KM;

    /** Working days from pickup to delivery. 0 means same day. */
    @Column(name = "transit_days", nullable = false)
    private Integer transitDays = 1;

    /** The remainder hours on top of {@link #transitDays}, {@code [0, 23]}. */
    @Column(name = "transit_hours", nullable = false)
    private Integer transitHours = 0;

    /** Intermediate points, free text, e.g. {@code "Nashik, Dhule"}. */
    @Column(name = "via", length = 255)
    private String via;

    @Override
    protected void applySpecificInvariants() {
        if (bookingBranchId == null || deliveryBranchId == null) {
            throw new BusinessRuleException(
                    "A route needs both a booking branch and a delivery branch.");
        }
        if (Objects.equals(bookingBranchId, deliveryBranchId)) {
            throw new BusinessRuleException(
                    "A route's booking and delivery branches must be different.");
        }
        if (distanceKm != null && distanceKm.signum() < 0) {
            throw new BusinessRuleException("Distance cannot be negative.");
        }
        if (transitDays == null) {
            this.transitDays = 1;
        }
        if (transitDays < 0) {
            throw new BusinessRuleException("Transit days cannot be negative.");
        }
        if (transitHours == null) {
            this.transitHours = 0;
        }
        if (transitHours < 0 || transitHours > 23) {
            throw new BusinessRuleException(
                    "Transit hours must be between 0 and 23 — 24 or more belongs in transit days.");
        }
        if (distanceUnit == null) {
            this.distanceUnit = DistanceUnit.KM;
        }
        this.via = blankToNull(via);
    }
}
