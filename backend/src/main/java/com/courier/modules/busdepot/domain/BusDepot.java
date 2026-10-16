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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * An MSRTC bus depot or bus station, optionally assigned to the branch (or vendor — a
 * Pickup-Delivery Partner is a branch type) that handles it.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bus_depots")
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class BusDepot extends CompanyOwnedEntity {

    @Column(name = "depot_code", nullable = false, length = 30)
    private String depotCode;

    @Column(name = "depot_name", nullable = false, length = 150)
    private String depotName;

    @Enumerated(EnumType.STRING)
    @Column(name = "depot_type", nullable = false, length = 20)
    @Builder.Default
    private BusDepotType depotType = BusDepotType.BUS_DEPOT;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "division_id", columnDefinition = "BINARY(16)")
    private UUID divisionId;

    /** For a station: the depot that serves it. Always a BUS_DEPOT of the same division. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "parent_depot_id", columnDefinition = "BINARY(16)")
    private UUID parentDepotId;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(name = "contact_mobile", length = 20)
    private String contactMobile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BusDepotStatus status = BusDepotStatus.ACTIVE;

    public boolean isActive() {
        return status == BusDepotStatus.ACTIVE;
    }

    public void activate() {
        this.status = BusDepotStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = BusDepotStatus.INACTIVE;
    }

    public void applyInvariants() {
        this.depotCode = blankToNull(depotCode) == null ? null : depotCode.trim().toUpperCase();
        this.depotName = blankToNull(depotName);
        this.address = blankToNull(address);
        this.city = blankToNull(city);
        this.district = blankToNull(district);
        this.state = blankToNull(state);
        this.pincode = blankToNull(pincode);
        this.contactPerson = blankToNull(contactPerson);
        this.contactMobile = blankToNull(contactMobile);
        if (depotCode == null) {
            throw new BusinessRuleException("A bus depot needs a code.");
        }
        if (depotName == null) {
            throw new BusinessRuleException("A bus depot needs a name.");
        }
        if (depotType == null) {
            throw new BusinessRuleException("Choose whether this is a bus depot or a bus station.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
