package com.courier.modules.master.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * A locality within exactly one {@link City} — the level a delivery boy is assigned to.
 *
 * <p>"One Area belongs to one City" is the business rule, and it is a single non-null
 * column, not a join table. A join table would make "one" a convention that the next
 * feature quietly breaks.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_areas",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_areas_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_areas_name",
                        columnNames = {"company_id", "city_id", "name"})
        },
        indexes = {
                @Index(name = "idx_master_areas_city", columnList = "company_id, city_id"),
                @Index(name = "idx_master_areas_status", columnList = "company_id, status")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Area extends MasterDataEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "city_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID cityId;

    @Override
    protected void applySpecificInvariants() {
        if (cityId == null) {
            throw new BusinessRuleException("An area must belong to a city.");
        }
    }
}
