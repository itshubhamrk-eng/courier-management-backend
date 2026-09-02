package com.courier.modules.company.domain;

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
 * Which branch owns delivery/service for a pincode.
 *
 * <p>A branch serves many pincodes; a pincode is served by exactly one branch per
 * company — {@code uk_branch_pincode_pincode} is unique on {@code (company_id, pincode_id)}
 * alone, not the pair, so re-mapping a pincode to a different branch requires removing the
 * existing link first rather than silently creating a second owner.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "branch_pincode_mapping",
        uniqueConstraints = @UniqueConstraint(name = "uk_branch_pincode_pincode",
                columnNames = {"company_id", "pincode_id"}),
        indexes = @Index(name = "idx_branch_pincode_branch", columnList = "company_id, branch_id"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class BranchPincodeMapping extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID branchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "pincode_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID pincodeId;

    public void applyInvariants() {
        if (branchId == null) {
            throw new BusinessRuleException("A branch-pincode mapping needs a branch.");
        }
        if (pincodeId == null) {
            throw new BusinessRuleException("A branch-pincode mapping needs a pincode.");
        }
    }
}
