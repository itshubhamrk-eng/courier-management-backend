package com.courier.modules.customerpricing.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.Objects;
import java.util.UUID;

/**
 * One district — or one city inside a district — that belongs to a pricing zone.
 * {@code cityId == null} means the whole district. The customer id is denormalised from
 * the zone so "this location is already in another zone of the same customer" is one query.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "customer_zone_locations")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class ZoneLocation extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "zone_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID zoneId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID customerId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "district_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID districtId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "city_id", columnDefinition = "BINARY(16)")
    private UUID cityId;

    public boolean sameLocationAs(UUID otherDistrictId, UUID otherCityId) {
        return Objects.equals(districtId, otherDistrictId) && Objects.equals(cityId, otherCityId);
    }

    /**
     * Two locations clash when one is the whole district and the other is the same district
     * (or a city in it), or when both name the same city.
     */
    public boolean overlaps(UUID otherDistrictId, UUID otherCityId) {
        if (!Objects.equals(districtId, otherDistrictId)) {
            return false;
        }
        return cityId == null || otherCityId == null || Objects.equals(cityId, otherCityId);
    }
}
