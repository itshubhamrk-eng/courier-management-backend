package com.courier.modules.master.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.Set;
import java.util.UUID;

/**
 * A city, within exactly one {@link District}.
 *
 * <p>{@code cityTier} is a commercial grading the rate master will price against. It is a
 * short string rather than an enum because the tiers a courier sells on are its own
 * commercial invention and change without a release.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_cities",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_cities_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_cities_name",
                        columnNames = {"company_id", "district_id", "name"})
        },
        indexes = {
                @Index(name = "idx_master_cities_district", columnList = "company_id, district_id"),
                @Index(name = "idx_master_cities_status", columnList = "company_id, status")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class City extends MasterDataEntity {

    /** Recognised tiers. Open enough to extend, closed enough to keep typos out. */
    public static final Set<String> TIERS = Set.of("TIER_1", "TIER_2", "TIER_3", "TIER_4");

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "district_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID districtId;

    @Column(name = "is_metro", nullable = false)
    private boolean metro = false;

    @Column(name = "city_tier", length = 10)
    private String cityTier;

    @Override
    protected void applySpecificInvariants() {
        if (districtId == null) {
            throw new BusinessRuleException("A city must belong to a district.");
        }
        this.cityTier = upperOrNull(cityTier);
        if (cityTier != null && !TIERS.contains(cityTier)) {
            throw new BusinessRuleException(
                    "Invalid city tier '%s'. Allowed: %s."
                            .formatted(cityTier, String.join(", ", new java.util.TreeSet<>(TIERS))));
        }
    }
}
