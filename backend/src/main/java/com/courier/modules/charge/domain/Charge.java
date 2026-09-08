package com.courier.modules.charge.domain;

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

import java.util.UUID;

/**
 * A named charge configuration, scoped to one Service Type within a company — the parent
 * a {@link ChargeSetting} row belongs to.
 *
 * <p>Company-owned, like every operational record. {@code serviceTypeId} is a plain
 * {@code UUID} column with no foreign key — it names a row in
 * {@code com.courier.modules.master}'s own table, a different module, validated in
 * {@code ChargeServiceImpl} through that module's application service interface rather
 * than a physical FK. Same treatment {@code Rate.serviceTypeId} already gets.
 *
 * <p>This module is configuration only: nothing here is consumed by Shipment Booking, or
 * any freight/commission/wallet calculation, yet. A future calculation engine will read
 * these rows; this module only lets them be authored and validated.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "charges",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_charges_company_service_name",
                        columnNames = {"company_id", "service_type_id", "charge_name"})
        },
        indexes = {
                @Index(name = "idx_charges_status", columnList = "company_id, status"),
                @Index(name = "idx_charges_service_type", columnList = "company_id, service_type_id")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Charge extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "service_type_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "charge_name", nullable = false, length = 150)
    private String chargeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ChargeStatus status = ChargeStatus.ACTIVE;

    // ---------------------------------------------------------------- behaviour

    public boolean isActive() {
        return status == ChargeStatus.ACTIVE;
    }

    public void activate() {
        this.status = ChargeStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ChargeStatus.INACTIVE;
    }

    public void applyInvariants() {
        this.chargeName = chargeName == null ? null : chargeName.trim();

        if (chargeName == null || chargeName.isBlank()) {
            throw new BusinessRuleException("A charge name is required.");
        }
        if (serviceTypeId == null) {
            throw new BusinessRuleException("A charge must belong to a service type.");
        }
        if (status == null) {
            this.status = ChargeStatus.ACTIVE;
        }
    }
}
