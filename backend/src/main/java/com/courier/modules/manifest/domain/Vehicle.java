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
import java.time.LocalDate;
import java.util.UUID;

/**
 * The fleet a manifest may be dispatched with — a full fleet record (registration,
 * class, ownership dates, statutory document expiries, current base branch), used by
 * Dispatch/THC's "Assign Vehicle" picker via {@link #isActive()}.
 *
 * <p>{@code status} is the vehicle's current operational state (AVAILABLE/IN_USE/
 * MAINTENANCE/INACTIVE); {@code active} is the separate enable/disable toggle every
 * other module in this project exposes via activate/deactivate — a vehicle can be
 * {@code MAINTENANCE} and still {@code active = true} (still a fleet record in normal
 * use, just not driveable this week).
 *
 * <p>Deliberately no driver/trip/maintenance/expense/document relationship — those are
 * separate, unbuilt modules by design; this entity only carries the fields listed for
 * it.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    @Builder.Default
    private VehicleType vehicleType = VehicleType.OTHER;

    @Column(name = "make", length = 50)
    private String make;

    @Column(name = "model", length = 50)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", length = 20)
    private FuelType fuelType;

    @Column(name = "capacity_kg", precision = 12, scale = 3)
    private BigDecimal capacityKg;

    @Column(name = "current_odometer", precision = 12, scale = 2)
    private BigDecimal currentOdometer;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "puc_expiry")
    private LocalDate pucExpiry;

    @Column(name = "fitness_expiry")
    private LocalDate fitnessExpiry;

    @Column(name = "permit_expiry")
    private LocalDate permitExpiry;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    /** Base branch, FK-less like every other cross-module id in this project. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // ---------------------------------------------------------------- behaviour

    public boolean isActive() {
        return active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void applyInvariants() {
        if (vehicleNumber == null || vehicleNumber.isBlank()) {
            throw new BusinessRuleException("A vehicle needs a vehicle number.");
        }
        this.vehicleNumber = vehicleNumber.trim().toUpperCase();
        if (vehicleType == null) {
            this.vehicleType = VehicleType.OTHER;
        }
        if (capacityKg != null && capacityKg.signum() < 0) {
            throw new BusinessRuleException("Vehicle capacity cannot be negative.");
        }
        if (currentOdometer != null && currentOdometer.signum() < 0) {
            throw new BusinessRuleException("Current odometer cannot be negative.");
        }
        if (status == null) {
            this.status = VehicleStatus.AVAILABLE;
        }
        this.make = make == null || make.isBlank() ? null : make.trim();
        this.model = model == null || model.isBlank() ? null : model.trim();
        this.remarks = remarks == null || remarks.isBlank() ? null : remarks.trim();
    }
}
