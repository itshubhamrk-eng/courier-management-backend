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
 * A grant: one company's role holds one permission.
 *
 * <p>Replaces the {@code company_role_permissions} element collection. As a real entity
 * the grant carries its own identity and audit columns, so "who gave this role the right
 * to cancel shipments, and when" is answerable — which is the first question after an
 * incident, and one the element collection could not answer at all.
 *
 * <p><b>Company-owned.</b> The {@code companyId} is redundant with the role's own, and
 * deliberately so: it lets the Hibernate filter apply to this table directly, without a
 * join to {@code company_roles} on every read.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_role_permissions_role_permission",
                columnNames = {"company_id", "role_id", "permission_id"}),
        indexes = {
                @Index(name = "idx_role_permissions_role", columnList = "company_id, role_id"),
                @Index(name = "idx_role_permissions_permission",
                        columnList = "company_id, permission_id")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class RolePermission extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "role_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID roleId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "permission_id", columnDefinition = "BINARY(16)",
            nullable = false, updatable = false)
    private UUID permissionId;

    /**
     * Denormalised copy of {@code Permission.permissionCode}.
     *
     * <p>Every authorisation decision needs the code, not the id. Storing it here turns
     * "what may this role do" into one indexed read instead of a join to a
     * platform-level table on the hot path. The code is immutable, so the copy cannot go
     * stale — that is exactly why it was made immutable.
     */
    @Column(name = "permission_code", nullable = false, updatable = false, length = 100)
    private String permissionCode;

    public static RolePermission grant(UUID roleId, Permission permission) {
        return RolePermission.builder()
                .roleId(roleId)
                .permissionId(permission.getId())
                .permissionCode(permission.getPermissionCode())
                .build();
    }
}
