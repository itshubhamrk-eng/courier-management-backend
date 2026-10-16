package com.courier.modules.busdepot.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable crossing route: a bus from {@code fromDepotId} to {@code toDepotId} that has
 * no direct service and goes through the ordered {@code stops} instead. Depot or station,
 * any combination — it never refers to a branch.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bus_crossing_routes")
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class BusCrossingRoute extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "from_depot_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID fromDepotId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "to_depot_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID toDepotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BusDepotStatus status = BusDepotStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bus_crossing_route_stops", joinColumns = @JoinColumn(name = "route_id"))
    @OrderColumn(name = "seq")
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "depot_id", columnDefinition = "BINARY(16)", nullable = false)
    @Builder.Default
    private List<UUID> stops = new ArrayList<>();

    public boolean isActive() {
        return status == BusDepotStatus.ACTIVE;
    }

    public void replaceStops(List<UUID> newStops) {
        stops.clear();
        stops.addAll(newStops);
    }
}
