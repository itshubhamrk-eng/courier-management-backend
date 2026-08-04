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
 * A class of vehicle the fleet runs — BIKE, AUTO, PICKUP, TRUCK, CONTAINER and whatever
 * else a company operates.
 *
 * <p>A table, not an enum, for the reason recorded as decision 28 in
 * {@code MEMORY/AI_CONTEXT.md}: a catalogue that must be listed, searched, extended and
 * given capacities by each company is data. A courier adding an EV three-wheeler should
 * not need a release.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_vehicle_types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_vehicle_types_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_vehicle_types_name",
                        columnNames = {"company_id", "name"})
        },
        indexes = @Index(name = "idx_master_vehicle_types_status",
                columnList = "company_id, status"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class VehicleType extends MasterDataEntity {

    /** Payload capacity. {@code DECIMAL}, never {@code double} — ARCHITECTURE §4. */
    @Column(name = "capacity_kg", precision = 12, scale = 3)
    private BigDecimal capacityKg;

    /** Load volume in cubic feet, the unit Indian fleet paperwork uses. */
    @Column(name = "capacity_cft", precision = 12, scale = 3)
    private BigDecimal capacityCft;

    @Column(name = "wheel_count")
    private Integer wheelCount;

    /** Whether the class needs a commercial permit before it may be dispatched. */
    @Column(name = "requires_permit", nullable = false)
    private boolean requiresPermit = false;

    @Override
    protected void applySpecificInvariants() {
        requirePositive(capacityKg, "Capacity (kg)");
        requirePositive(capacityCft, "Capacity (cft)");
        if (wheelCount != null && wheelCount <= 0) {
            throw new BusinessRuleException("Wheel count must be greater than zero.");
        }
    }

    private static void requirePositive(BigDecimal value, String label) {
        if (value != null && value.signum() <= 0) {
            throw new BusinessRuleException("%s must be greater than zero.".formatted(label));
        }
    }
}
