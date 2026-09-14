package com.courier.modules.company.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.util.UUID;

/**
 * A grant: one department offers one company role to whoever is placed in it.
 *
 * <p>Same shape as {@link RolePermission} — a real join entity rather than an
 * {@code @ManyToMany}, so the denormalised {@code roleCode} turns "which roles does this
 * department offer" into one indexed read instead of a join to {@code company_roles} on
 * the user-creation hot path.
 *
 * <p>Company-owned, with {@code companyId} redundant with the department's own and
 * deliberately so — see {@code RolePermission}'s own note.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "department_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_department_roles_department_role",
                columnNames = {"company_id", "department_id", "role_id"}),
        indexes = {
                @Index(name = "idx_department_roles_department", columnList = "company_id, department_id"),
                @Index(name = "idx_department_roles_role", columnList = "company_id, role_id")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class DepartmentRole extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "department_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID departmentId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "role_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID roleId;

    /** Denormalised copy of {@code CompanyRole.roleCode}. Immutable, so it cannot go stale. */
    @Column(name = "role_code", nullable = false, updatable = false, length = 50)
    private String roleCode;

    public static DepartmentRole grant(UUID departmentId, CompanyRole role) {
        return DepartmentRole.builder()
                .departmentId(departmentId)
                .roleId(role.getId())
                .roleCode(role.getRoleCode())
                .build();
    }
}
