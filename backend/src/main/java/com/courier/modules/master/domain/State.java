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
 * A state or province, within exactly one {@link Country}.
 *
 * <p>The parent is a plain {@code UUID} column rather than a {@code @ManyToOne}: this
 * module never needs to navigate upward inside a transaction, and an association would
 * pull a lazy proxy — and the Hibernate company filter — into every read of a picker list.
 * The service validates that the parent exists, belongs to the same company and is active.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "master_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_master_states_code",
                        columnNames = {"company_id", "code"}),
                @UniqueConstraint(name = "uk_master_states_name",
                        columnNames = {"company_id", "country_id", "name"})
        },
        indexes = {
                @Index(name = "idx_master_states_country", columnList = "company_id, country_id"),
                @Index(name = "idx_master_states_status", columnList = "company_id, status")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class State extends MasterDataEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "country_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID countryId;

    /**
     * India's two-digit GST state code. Text, not a number: {@code 07} for Delhi loses its
     * leading zero the moment it becomes an int, and every invoice printed from it is wrong.
     */
    @Column(name = "gst_state_code", length = 4)
    private String gstStateCode;

    @Override
    protected void applySpecificInvariants() {
        if (countryId == null) {
            throw new BusinessRuleException("A state must belong to a country.");
        }
        this.gstStateCode = blankToNull(gstStateCode);
    }
}
