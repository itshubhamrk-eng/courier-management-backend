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
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * What is being carried — DOCUMENT, PARCEL, BOX, BAG, PALLET and anything else a company
 * books.
 *
 * <p>{@code documentType} matters beyond labelling: documents are usually rated on a flat
 * slab rather than by weight, and are exempt from the dimension capture a parcel needs.
 * The default dimensions are what the booking screen pre-fills, not a limit.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_package_types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_package_types_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_package_types_name",
                        columnNames = {"company_id", "name"})
        },
        indexes = @Index(name = "idx_master_package_types_status",
                columnList = "company_id, status"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PackageType extends MasterDataEntity {

    @Column(name = "is_document", nullable = false)
    private boolean documentType = false;

    /** Pre-ticks the fragile flag on the booking screen; the clerk may still clear it. */
    @Column(name = "fragile_by_default", nullable = false)
    private boolean fragileByDefault = false;

    /** Refuse a booking above this, if set. Null means no ceiling. */
    @Column(name = "max_weight_kg", precision = 12, scale = 3)
    private BigDecimal maxWeightKg;

    @Column(name = "default_length_cm", precision = 10, scale = 2)
    private BigDecimal defaultLengthCm;

    @Column(name = "default_width_cm", precision = 10, scale = 2)
    private BigDecimal defaultWidthCm;

    @Column(name = "default_height_cm", precision = 10, scale = 2)
    private BigDecimal defaultHeightCm;

    @Override
    protected void applySpecificInvariants() {
        requirePositive(maxWeightKg, "Maximum weight");
        requirePositive(defaultLengthCm, "Default length");
        requirePositive(defaultWidthCm, "Default width");
        requirePositive(defaultHeightCm, "Default height");
    }

    private static void requirePositive(BigDecimal value, String label) {
        if (value != null && value.signum() <= 0) {
            throw new BusinessRuleException("%s must be greater than zero.".formatted(label));
        }
    }
}
