package com.courier.modules.pdp.domain;

import com.courier.modules.customer.domain.BillingFrequency;
import com.courier.shared.domain.CompanyOwnedEntity;
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

/** How often one partner is billed. A partner without a row is billed {@link #DEFAULT}. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pdp_billing_settings")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PdpBillingSetting extends CompanyOwnedEntity {

    public static final BillingFrequency DEFAULT = BillingFrequency.MONTHLY;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_frequency", nullable = false, length = 20)
    @Builder.Default
    private BillingFrequency billingFrequency = DEFAULT;
}
