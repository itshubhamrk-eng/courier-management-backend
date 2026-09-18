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
 * One user's explicit delta on top of their role's default permissions.
 *
 * <p>A user's <b>effective</b> permissions are always {@code role codes ∪ granted
 * overrides ∖ revoked overrides} — see {@code UserPermissionService
 * .resolveEffectivePermissionCodes}. This table therefore only ever holds rows where the
 * admin's choice for a user actually differs from what their role would already give
 * them; a checkbox left at the role default gets no row at all. That is what keeps this
 * a genuine per-user *override* rather than a second, parallel grant system: the role's
 * own {@link RolePermission} grants are untouched by anything written here, and a role's
 * defaults changing later is still honoured for every user who never overrode that
 * particular right.
 *
 * <p>Company-owned, same shape as {@link RolePermission} (denormalised
 * {@code permissionCode} for the same reason: every authorisation decision needs the
 * code, not the id).
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "user_permission_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_permission_overrides_user_permission",
                columnNames = {"company_id", "user_id", "permission_id"}),
        indexes = {
                @Index(name = "idx_user_permission_overrides_user", columnList = "company_id, user_id")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class UserPermissionOverride extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "permission_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID permissionId;

    @Column(name = "permission_code", nullable = false, updatable = false, length = 100)
    private String permissionCode;

    /**
     * {@code true} — explicitly granted even though the role does not hold it.
     * {@code false} — explicitly revoked even though the role does hold it.
     */
    @Column(name = "granted", nullable = false)
    private boolean granted;

    public static UserPermissionOverride of(UUID userId, Permission permission, boolean granted) {
        return UserPermissionOverride.builder()
                .userId(userId)
                .permissionId(permission.getId())
                .permissionCode(permission.getPermissionCode())
                .granted(granted)
                .build();
    }
}
