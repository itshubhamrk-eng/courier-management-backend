package com.courier.modules.busdepot.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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

/** The crossing route one MSRTC shipment travels, copied from the route at the time. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "shipment_bus_crossings")
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ShipmentBusCrossing extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID shipmentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "route_id", columnDefinition = "BINARY(16)")
    private UUID routeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "from_depot_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID fromDepotId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "to_depot_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID toDepotId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shipment_bus_crossing_stops", joinColumns = @JoinColumn(name = "crossing_id"))
    @OrderColumn(name = "seq")
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "depot_id", columnDefinition = "BINARY(16)", nullable = false)
    @Builder.Default
    private List<UUID> stops = new ArrayList<>();

    public void replaceStops(List<UUID> newStops) {
        stops.clear();
        stops.addAll(newStops);
    }
}
