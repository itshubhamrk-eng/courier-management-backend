package com.courier.modules.busdepot.domain;

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
import org.hibernate.annotations.SQLRestriction;

/** An MSRTC division: the unit that groups depots and bus stations. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bus_divisions")
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class BusDivision extends CompanyOwnedEntity {

    @Column(name = "division_code", nullable = false, length = 30)
    private String divisionCode;

    @Column(name = "division_name", nullable = false, length = 100)
    private String divisionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BusDepotStatus status = BusDepotStatus.ACTIVE;

    public void activate() {
        this.status = BusDepotStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = BusDepotStatus.INACTIVE;
    }

    public void applyInvariants() {
        this.divisionCode = divisionCode == null || divisionCode.isBlank() ? null : divisionCode.trim().toUpperCase();
        this.divisionName = divisionName == null || divisionName.isBlank() ? null : divisionName.trim();
        if (divisionCode == null) {
            throw new BusinessRuleException("A division needs a code.");
        }
        if (divisionName == null) {
            throw new BusinessRuleException("A division needs a name.");
        }
    }
}
