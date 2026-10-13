package com.courier.modules.customerpricing.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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
 * A named group of districts/cities that one business customer is priced by. Belongs to
 * exactly one customer; the name is unique among that customer's live zones (checked in
 * the service — a soft-deleted zone must not reserve its name).
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "customer_pricing_zones")
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PricingZone extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ZoneStatus status = ZoneStatus.ACTIVE;

    public boolean isActive() {
        return status == ZoneStatus.ACTIVE;
    }

    public void activate() {
        this.status = ZoneStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = ZoneStatus.INACTIVE;
    }

    public void applyInvariants() {
        this.zoneName = zoneName == null ? null : zoneName.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        if (zoneName == null || zoneName.isBlank()) {
            throw new BusinessRuleException("A pricing zone needs a name.");
        }
        if (customerId == null) {
            throw new BusinessRuleException("A pricing zone must belong to a customer.");
        }
    }
}
