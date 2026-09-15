package com.courier.modules.company.domain;

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

/**
 * An organisational department inside one company — Sales, Operations, Accounts and the
 * like — used to group which roles a new user in that department may hold.
 *
 * <p>Which roles a department grants is not held here: it is rows in
 * {@code department_roles}, a join to {@code company_roles}, the same shape
 * {@code RolePermission} already uses for role-to-permission grants. A department may
 * carry several roles — Operations might offer both {@code BOOKING_OPERATOR} and
 * {@code DELIVERY_OPERATOR} — and a user creation picks one of the department's own roles
 * rather than the whole company catalogue.
 *
 * <p>Company-owned: every company defines its own departments, so one company renaming or
 * retiring a department cannot affect another's.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "departments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_departments_company_code", columnNames = {"company_id", "department_code"}),
        indexes = {
                @Index(name = "idx_departments_company", columnList = "company_id, status")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass,
// and an unfiltered company-owned entity is a cross-company data leak.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class Department extends CompanyOwnedEntity {

    @Column(name = "department_code", nullable = false, updatable = false, length = 50)
    private String departmentCode;

    @Column(name = "department_name", nullable = false, length = 100)
    private String departmentName;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DepartmentStatus status = DepartmentStatus.ACTIVE;

    public boolean isActive() {
        return status == DepartmentStatus.ACTIVE;
    }

    public void activate() {
        this.status = DepartmentStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = DepartmentStatus.INACTIVE;
    }

    /** Normalises the fields that are matched on. Called before every save. */
    public void applyInvariants() {
        this.departmentCode = normaliseCode(departmentCode);
        this.departmentName = departmentName == null ? null : departmentName.trim();
        if (this.status == null) {
            this.status = DepartmentStatus.ACTIVE;
        }
    }

    public static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase().replace(' ', '_');
    }
}
