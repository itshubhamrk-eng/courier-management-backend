package com.courier.modules.company.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * What an admin ticked for ONE menu item of ONE user. Independent of every other menu,
 * including the ones that share its {@link PermissionModule}: the module-level
 * {@link UserPermissionOverride}s are derived from these rows (union over the module's
 * menus), never the other way round. Rows are hard-deleted, never soft-deleted.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "user_menu_grants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_menu_grants_user_menu", columnNames = {"company_id", "user_id", "menu_item_id"}))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class UserMenuGrant extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "menu_item_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID menuItemId;

    @Column(name = "can_create", nullable = false)
    private boolean canCreate;

    @Column(name = "can_read", nullable = false)
    private boolean canRead;

    @Column(name = "can_update", nullable = false)
    private boolean canUpdate;

    @Column(name = "can_delete", nullable = false)
    private boolean canDelete;

    public boolean allows(PermissionAction action) {
        return switch (action) {
            case CREATE -> canCreate;
            case READ -> canRead;
            case UPDATE -> canUpdate;
            case DELETE -> canDelete;
            default -> false;
        };
    }
}
