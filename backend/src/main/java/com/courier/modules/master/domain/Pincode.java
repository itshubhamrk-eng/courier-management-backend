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

import java.util.UUID;

/**
 * A postal code, belonging to exactly one {@link Area}.
 *
 * <p>The inherited {@code code} <i>is</i> the pincode; {@code name} is the post office or
 * locality label printed alongside it. Storing the digits in the shared {@code code}
 * column rather than a second {@code pincode} column is what lets the same search,
 * uniqueness check and sort whitelist serve this list as every other.
 *
 * <p>The serviceability flags are read on every booking, which is why they are columns and
 * not a settings blob: {@code serviceable} decides whether the pincode may be booked to at
 * all, and the payment flags decide which payment modes the clerk is offered.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_pincodes",
        uniqueConstraints = @UniqueConstraint(name = "uk_master_pincodes_code",
                columnNames = {"company_id", "code"}),
        indexes = {
                @Index(name = "idx_master_pincodes_area", columnList = "company_id, area_id"),
                @Index(name = "idx_master_pincodes_status", columnList = "company_id, status"),
                @Index(name = "idx_master_pincodes_serviceable",
                        columnList = "company_id, serviceable")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Pincode extends MasterDataEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "area_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID areaId;

    @Column(name = "serviceable", nullable = false)
    private boolean serviceable = true;

    @Column(name = "cod_available", nullable = false)
    private boolean codAvailable = true;

    @Column(name = "prepaid_available", nullable = false)
    private boolean prepaidAvailable = true;

    @Column(name = "pickup_available", nullable = false)
    private boolean pickupAvailable = true;

    /** Delivery zone the rate master prices against, e.g. {@code A} or {@code LOCAL}. */
    @Column(name = "zone", length = 20)
    private String zone;

    @Override
    protected void applySpecificInvariants() {
        if (areaId == null) {
            throw new BusinessRuleException("A pincode must belong to an area.");
        }
        this.zone = upperOrNull(zone);

        // The code is the postal code itself, so it is digits — not the letters-and-
        // underscores a code normally allows. 4 to 10 covers every national scheme this
        // is likely to meet without hard-coding India's six.
        if (getCode() != null && !getCode().matches("^[0-9]{4,10}$")) {
            throw new BusinessRuleException(
                    "A pincode must be 4 to 10 digits. Got '%s'.".formatted(getCode()));
        }
        if (!serviceable && (codAvailable || prepaidAvailable || pickupAvailable)) {
            // Refusing this outright would make "stop servicing this pincode" a
            // multi-step edit, so the flags are folded down instead. Silent, but the
            // audit entry records the change.
            this.codAvailable = false;
            this.prepaidAvailable = false;
            this.pickupAvailable = false;
        }
    }
}
