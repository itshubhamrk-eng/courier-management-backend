package com.courier.modules.manifest.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The fleet a manifest may be dispatched with. Deliberately minimal — a registration
 * number, an optional {@code master.domain.VehicleType} category and capacity — this
 * module's own Definition of Done only asks for enough to populate Dispatch's "Assign
 * Vehicle" picker, not a full fleet-management module (maintenance, insurance expiry,
 * odometer, ...). {@code vehicleTypeId} carries no physical FK, the same cross-module
 * treatment every other master-data id in this project gets.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vehicles",
        uniqueConstraints = @UniqueConstraint(name = "uk_vehicles_company_number",
                columnNames = {"company_id", "vehicle_number"}))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Vehicle extends CompanyOwnedEntity {

    @Column(name = "vehicle_number", nullable = false, length = 30)
    private String vehicleNumber;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "vehicle_type_id", columnDefinition = "BINARY(16)")
    private UUID vehicleTypeId;

    @Column(name = "capacity_kg", precision = 12, scale = 3)
    private BigDecimal capacityKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VehicleStatus status = VehicleStatus.ACTIVE;

    @Column(name = "remarks", length = 500)
    private String remarks;

    public boolean isActive() {
        return status == VehicleStatus.ACTIVE;
    }

    public void applyInvariants() {
        if (vehicleNumber == null || vehicleNumber.isBlank()) {
            throw new BusinessRuleException("A vehicle needs a vehicle number.");
        }
        this.vehicleNumber = vehicleNumber.trim().toUpperCase();
        if (capacityKg != null && capacityKg.signum() < 0) {
            throw new BusinessRuleException("Vehicle capacity cannot be negative.");
        }
        this.remarks = remarks == null || remarks.isBlank() ? null : remarks.trim();
    }
}
