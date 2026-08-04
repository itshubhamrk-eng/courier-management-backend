package com.courier.modules.company.domain;

import com.courier.shared.domain.BaseEntity;
import com.courier.shared.exception.BusinessRuleException;
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

import java.util.Map;

/**
 * One right that can be granted to a role.
 *
 * <p><b>This was an enum until Phase 3.</b> Thirty constants were fine while permissions
 * were hard-coded; a catalogue of ~200 across 28 modules that operators must be able to
 * list, search and extend is a table. The old {@code company_role_permissions} element
 * collection is now {@link RolePermission}.
 *
 * <p><b>Platform-level, not company-owned.</b> The catalogue is shared: every company sees
 * the same rights, and only {@code SUPER_ADMIN} writes it. What differs per company is
 * which permissions its roles hold, which is what {@link RolePermission} records.
 *
 * <p>{@link #permissionCode} is derived from {@code module + "_" + action} and is
 * immutable — roles reference it, and it appears in audit rows.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "permissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_permissions_code", columnNames = "permission_code"),
                @UniqueConstraint(name = "uk_permissions_module_action",
                        columnNames = {"module", "action"})
        },
        indexes = {
                @Index(name = "idx_permissions_module", columnList = "module, display_order"),
                @Index(name = "idx_permissions_status", columnList = "status"),
                @Index(name = "idx_permissions_resource", columnList = "resource")
        })
@SQLRestriction("deleted = false")
public class Permission extends BaseEntity {

    @Column(name = "permission_code", nullable = false, updatable = false, length = 100)
    private String permissionCode;

    @Column(name = "permission_name", nullable = false, length = 150)
    private String permissionName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "module", nullable = false, updatable = false, length = 30)
    private PermissionModule module;

    /**
     * The thing acted upon, in the URL's spelling — {@code shipments}, {@code rate-cards}.
     * Held separately from {@link #module} so an authorisation filter can eventually map
     * a request path to a permission without a lookup table.
     */
    @Column(name = "resource", nullable = false, length = 60)
    private String resource;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action", nullable = false, updatable = false, length = 20)
    private PermissionAction action;

    @Column(name = "description", length = 255)
    private String description;

    /**
     * Seeded by the platform. System permissions are <b>read-only</b>: they may not be
     * edited or deleted, because a company's roles and the application's own
     * {@code @PreAuthorize} expressions both reference their codes.
     */
    @Column(name = "is_system_permission", nullable = false)
    @Builder.Default
    private boolean systemPermission = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PermissionStatus status = PermissionStatus.ACTIVE;

    /**
     * No {@code @Builder.Default}: it must stay null until {@link #applyInvariants}
     * derives it from the module and action. A default of 0 here would never be null,
     * so the derivation would never run and every API-created permission would sort to
     * the top of the list.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /**
     * Subscription feature this permission depends on, or null when unconditional.
     *
     * <p>Kept from the enum it replaced: a plan that excludes bulk booking must not have
     * that right granted to any of its roles, however the grant is attempted. Not part of
     * the requested field list, but dropping it would silently remove a control that
     * already existed.
     */
    @Column(name = "required_feature_flag", length = 50)
    private String requiredFeatureFlag;

    // ---------------------------------------------------------------- behaviour

    /** The canonical code for a module/action pair. The single place this is formed. */
    public static String codeFor(PermissionModule module, PermissionAction action) {
        return module.name() + "_" + action.name();
    }

    public static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase().replace(' ', '_');
    }

    public boolean isPlanGated() {
        return requiredFeatureFlag != null && !requiredFeatureFlag.isBlank();
    }

    /**
     * @param featureFlags the company's plan flags; a missing or non-{@code true} value
     *                     denies, so an unknown flag fails closed
     */
    public boolean isAllowedByPlan(Map<String, Object> featureFlags) {
        if (!isPlanGated()) {
            return true;
        }
        return featureFlags != null && Boolean.TRUE.equals(featureFlags.get(requiredFeatureFlag));
    }

    public boolean isActive() {
        return status == PermissionStatus.ACTIVE;
    }

    public void activate() {
        this.status = PermissionStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = PermissionStatus.INACTIVE;
    }

    /** Rejects any edit to a seeded permission. Called before update and delete. */
    public void requireEditable() {
        if (systemPermission) {
            throw new BusinessRuleException(
                    "%s is a system permission and is read-only.".formatted(permissionCode));
        }
    }

    /** Derives the code and normalises the rest. Called before every save. */
    public void applyInvariants() {
        if (module == null || action == null) {
            throw new BusinessRuleException("A permission needs both a module and an action.");
        }
        // The code is always derived, never taken from the caller: two permissions whose
        // code disagreed with their module/action pair would be impossible to reason about.
        this.permissionCode = codeFor(module, action);
        this.permissionName = permissionName == null || permissionName.isBlank()
                ? defaultName()
                : permissionName.trim();
        this.resource = resource == null || resource.isBlank()
                ? module.name().toLowerCase().replace('_', '-')
                : resource.trim().toLowerCase();
        if (status == null) {
            this.status = PermissionStatus.ACTIVE;
        }
        if (displayOrder == null) {
            this.displayOrder = module.displayOrder() + action.displayOffset();
        }
    }

    private String defaultName() {
        String moduleName = module.displayName();
        String actionName = action.name().charAt(0) + action.name().substring(1).toLowerCase();
        return actionName + " " + moduleName;
    }
}
