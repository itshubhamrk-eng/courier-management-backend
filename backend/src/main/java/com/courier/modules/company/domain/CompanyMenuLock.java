package com.courier.modules.company.domain;

import com.courier.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * A sidebar menu a {@code SUPER_ADMIN} has hidden from one company. Absence of a row means
 * visible. Platform-level (extends {@link BaseEntity}, not {@code CompanyOwnedEntity}): it
 * is written by a tenant-unbound super admin about a company, so the company id is an
 * ordinary column and no tenant filter applies. Rows are hard-deleted on unlock.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "company_menu_locks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_company_menu_locks_company_code", columnNames = {"company_id", "menu_code"}))
public class CompanyMenuLock extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "company_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID companyId;

    /** The frontend navigation node id, e.g. {@code msrtc}. */
    @Column(name = "menu_code", nullable = false, updatable = false, length = 80)
    private String menuCode;
}
