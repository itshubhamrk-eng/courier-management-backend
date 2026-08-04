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

/** A district, within exactly one {@link State}. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_districts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_districts_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_districts_name",
                        columnNames = {"company_id", "state_id", "name"})
        },
        indexes = {
                @Index(name = "idx_master_districts_state", columnList = "company_id, state_id"),
                @Index(name = "idx_master_districts_status", columnList = "company_id, status")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class District extends MasterDataEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "state_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID stateId;

    @Override
    protected void applySpecificInvariants() {
        if (stateId == null) {
            throw new BusinessRuleException("A district must belong to a state.");
        }
    }
}
