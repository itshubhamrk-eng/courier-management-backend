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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One row of a depot / bus-station timetable board: a place, the times buses leave the
 * depot for it ({@code departures}) and arrive from it ({@code arrivals}), in board order.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bus_timetable_entries")
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class BusTimetableEntry extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "depot_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID depotId;

    @Column(name = "place_name", nullable = false, length = 150)
    private String placeName;

    /** Highlighted (red) on the MSRTC board: a major / long-distance place. */
    @Column(name = "is_major", nullable = false)
    private boolean major;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bus_timetable_departures", joinColumns = @JoinColumn(name = "entry_id"))
    @OrderColumn(name = "seq")
    @Column(name = "dep_time", nullable = false)
    @Builder.Default
    private List<LocalTime> departures = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bus_timetable_arrivals", joinColumns = @JoinColumn(name = "entry_id"))
    @OrderColumn(name = "seq")
    @Column(name = "arr_time", nullable = false)
    @Builder.Default
    private List<LocalTime> arrivals = new ArrayList<>();
}
