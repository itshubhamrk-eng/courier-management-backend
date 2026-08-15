package com.courier.modules.distance.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A resolved road distance + travel time between two addresses of the same {@link
 * AddressType} — both branches, or both customers. {@code fromId}/{@code toId} are not
 * FKs: which table they point into is decided by {@code addressType} alone, the same
 * "no cross-entity FK until the data is stable" reasoning {@code Branch.managerId} uses.
 *
 * <p>Ordered, not symmetric: A→B is its own row from B→A, since a routed distance need
 * not be equal in both directions.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "address_distance",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_address_distance_pair",
                        columnNames = {"company_id", "address_type", "from_id", "to_id"})
        },
        indexes = {
                @Index(name = "idx_address_distance_from", columnList = "company_id, address_type, from_id"),
                @Index(name = "idx_address_distance_to", columnList = "company_id, address_type, to_id")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class AddressDistance extends CompanyOwnedEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "address_type", nullable = false, length = 20, updatable = false)
    private AddressType addressType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "from_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID fromId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "to_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID toId;

    @Column(name = "distance_km", precision = 10, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "distance_meter", precision = 12, scale = 2)
    private BigDecimal distanceMeter;

    /** Travel time as returned by the routing lookup, in minutes. */
    @Column(name = "required_time_minutes", precision = 10, scale = 2)
    private BigDecimal requiredTimeMinutes;
}
