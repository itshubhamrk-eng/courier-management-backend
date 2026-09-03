package com.courier.modules.districtfreight.domain;

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
import java.util.Optional;
import java.util.UUID;

/**
 * One rate-setup row: From Station (a company's own {@code Branch}) + Destination District
 * (the global District master) + a fixed six-slab per-KG rate table, plus a per-row ODA
 * charge.
 *
 * <p>{@code branchId}/{@code districtId} carry no physical FK on the Java side — plain
 * {@code UUID} columns validated through this module's own {@link BranchLookupPort}/
 * {@link DistrictLookupPort} seams, the same arrangement {@code Route.bookingBranchId}
 * and {@code BranchPincodeMapping.pincodeId} already use. The migration itself does add
 * real DB foreign keys (unlike {@code Route}) since neither {@code branches} nor
 * {@code master_districts} is at risk of being dropped out from under this table.
 *
 * <p>{@link #ratePerKgFor(BigDecimal)}/{@link #matchWeightSlab(BigDecimal)} are pure
 * lookups: the COMPLETE weight prices at exactly one slab's per-KG rate, never a
 * progressive split across slabs. Called from {@code districtfreight.application
 * .FreightCalculationServiceImpl}, Shipment Booking's own freight source (see
 * {@code ShipmentServiceImpl.priceIt}).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "district_level_freight",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_district_freight_combo",
                        columnNames = {"company_id", "branch_id", "district_id"})
        },
        indexes = {
                @Index(name = "idx_district_freight_branch", columnList = "company_id, branch_id"),
                @Index(name = "idx_district_freight_district", columnList = "company_id, district_id"),
                @Index(name = "idx_district_freight_status", columnList = "company_id, status")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class DistrictLevelFreight extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID branchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "district_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID districtId;

    @Column(name = "rate_1_to_15", nullable = false, precision = 19, scale = 4)
    private BigDecimal rate1To15;

    @Column(name = "rate_16_to_50", nullable = false, precision = 19, scale = 4)
    private BigDecimal rate16To50;

    @Column(name = "rate_51_to_100", nullable = false, precision = 19, scale = 4)
    private BigDecimal rate51To100;

    @Column(name = "rate_101_to_1000", nullable = false, precision = 19, scale = 4)
    private BigDecimal rate101To1000;

    @Column(name = "rate_1001_to_1500", nullable = false, precision = 19, scale = 4)
    private BigDecimal rate1001To1500;

    @Column(name = "rate_1501_to_2000", nullable = false, precision = 19, scale = 4)
    private BigDecimal rate1501To2000;

    @Column(name = "oda_applicable", nullable = false)
    private boolean odaApplicable = true;

    @Column(name = "oda_charge", nullable = false, precision = 19, scale = 4)
    private BigDecimal odaCharge = new BigDecimal("250.0000");

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private DistrictFreightStatus status = DistrictFreightStatus.ACTIVE;

    // ---------------------------------------------------------------- behaviour

    public boolean isActive() {
        return status == DistrictFreightStatus.ACTIVE;
    }

    public void activate() {
        this.status = DistrictFreightStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = DistrictFreightStatus.INACTIVE;
    }

    /**
     * The per-KG rate for the slab that {@code completeWeightKg} falls in, closed on both
     * ends, {@code [1, 2000]}. Empty when the weight is outside every configured slab (0,
     * negative, or over 2000 kg) — the caller decides what that means; this entity does not
     * throw for a shape that is a booking-time concern, not a configuration-time one.
     *
     * <p>Not called from anywhere yet — see the class javadoc.
     */
    public Optional<BigDecimal> ratePerKgFor(BigDecimal completeWeightKg) {
        if (completeWeightKg == null) {
            return Optional.empty();
        }
        if (between(completeWeightKg, 1, 15)) {
            return Optional.of(rate1To15);
        }
        if (between(completeWeightKg, 16, 50)) {
            return Optional.of(rate16To50);
        }
        if (between(completeWeightKg, 51, 100)) {
            return Optional.of(rate51To100);
        }
        if (between(completeWeightKg, 101, 1000)) {
            return Optional.of(rate101To1000);
        }
        if (between(completeWeightKg, 1001, 1500)) {
            return Optional.of(rate1001To1500);
        }
        if (between(completeWeightKg, 1501, 2000)) {
            return Optional.of(rate1501To2000);
        }
        return Optional.empty();
    }

    private static boolean between(BigDecimal value, int lo, int hi) {
        return value.compareTo(BigDecimal.valueOf(lo)) >= 0 && value.compareTo(BigDecimal.valueOf(hi)) <= 0;
    }

    /** The slab label + rate together, for a caller (Shipment Booking's own freight
     *  calculation) that needs to show which slab matched, not just its rate. Same
     *  boundaries as {@link #ratePerKgFor} — kept in sync by construction, not by
     *  duplicating the boundary checks a second time. */
    public record SlabMatch(String label, BigDecimal ratePerKg) {
    }

    public Optional<SlabMatch> matchWeightSlab(BigDecimal completeWeightKg) {
        if (completeWeightKg == null) {
            return Optional.empty();
        }
        if (between(completeWeightKg, 1, 15)) {
            return Optional.of(new SlabMatch("1-15 KG", rate1To15));
        }
        if (between(completeWeightKg, 16, 50)) {
            return Optional.of(new SlabMatch("16-50 KG", rate16To50));
        }
        if (between(completeWeightKg, 51, 100)) {
            return Optional.of(new SlabMatch("51-100 KG", rate51To100));
        }
        if (between(completeWeightKg, 101, 1000)) {
            return Optional.of(new SlabMatch("101-1000 KG", rate101To1000));
        }
        if (between(completeWeightKg, 1001, 1500)) {
            return Optional.of(new SlabMatch("1001-1500 KG", rate1001To1500));
        }
        if (between(completeWeightKg, 1501, 2000)) {
            return Optional.of(new SlabMatch("1501-2000 KG", rate1501To2000));
        }
        return Optional.empty();
    }

    public void applyInvariants() {
        if (branchId == null) {
            throw new BusinessRuleException("A From Station (branch) is required.");
        }
        if (districtId == null) {
            throw new BusinessRuleException("A destination district is required.");
        }
        requireNonNegative(rate1To15, "1 to 15 KG rate");
        requireNonNegative(rate16To50, "16 to 50 KG rate");
        requireNonNegative(rate51To100, "51 to 100 KG rate");
        requireNonNegative(rate101To1000, "101 to 1000 KG rate");
        requireNonNegative(rate1001To1500, "1001 to 1500 KG rate");
        requireNonNegative(rate1501To2000, "1501 to 2000 KG rate");
        requireNonNegative(odaCharge, "ODA charge");
        if (status == null) {
            this.status = DistrictFreightStatus.ACTIVE;
        }
    }

    private static void requireNonNegative(BigDecimal value, String label) {
        if (value == null || value.signum() < 0) {
            throw new BusinessRuleException(label + " cannot be negative.");
        }
    }
}
