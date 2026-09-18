package com.courier.modules.company.domain;

import com.courier.shared.domain.BaseEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * One node of the application's menu/submenu hierarchy, unlimited depth via
 * {@link #parentId} self-reference.
 *
 * <p><b>Platform-level, not company-owned</b> — same posture as {@link Permission}: every
 * company sees the same screens, and only {@code SUPER_ADMIN} edits the catalogue. What
 * differs per role/user is not the menu shape but which of its CRUD actions they hold,
 * which is exactly what {@link RolePermission}/{@code UserPermissionOverride} already
 * record — a leaf does not invent a new grant, it just names which
 * {@link PermissionModule} its CREATE/READ/UPDATE/DELETE checkboxes read and write.
 *
 * <p>A node with no {@link #permissionModule} is a pure grouping node (e.g. "Shipment
 * Management", "Manifest") — not itself grantable, only its leaves are.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "menu_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_menu_items_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_menu_items_parent", columnList = "parent_id, display_order"),
                @Index(name = "idx_menu_items_module", columnList = "permission_module")
        })
@SQLRestriction("deleted = false")
public class MenuItem extends BaseEntity {

    /** Null for a top-level node. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "parent_id", columnDefinition = "BINARY(16)")
    private UUID parentId;

    /** Stable identifier, matches the frontend nav config's own node id. Immutable. */
    @Column(name = "code", nullable = false, updatable = false, length = 80)
    private String code;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "icon", length = 60)
    private String icon;

    /** The Angular route this leaf opens, or null for a pure grouping node. */
    @Column(name = "route", length = 200)
    private String route;

    /**
     * Which permission module this leaf's CRUD checkboxes read/write, or null for a
     * grouping node. Not every module has all four actions — see
     * {@code DefaultPermissionCatalog}; a checkbox for an action the module lacks is
     * simply not offered.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "permission_module", length = 30)
    private PermissionModule permissionModule;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Seeded by the platform: read-only, never deletable — same posture as
     *  {@code Permission.systemPermission}. */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean system = false;

    public boolean isGroup() {
        return permissionModule == null;
    }
}
