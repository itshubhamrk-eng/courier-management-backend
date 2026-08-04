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
 * A role inside one company.
 *
 * <p><b>Permissions are no longer held here.</b> Until Phase 3 they were an
 * {@code @ElementCollection} of an enum; they are now rows in {@code role_permissions}
 * pointing at the {@code permissions} catalogue, so a grant carries its own audit trail
 * and the catalogue can grow past what an enum can express. Read them through
 * {@code RolePermissionService}.
 *
 * <p>Company-owned: every company gets its own eight rows at creation
 * (see {@code DefaultRoleCatalog}), so one company renaming or re-permissioning a role
 * cannot affect another's.
 *
 * <p>{@link #systemRole} marks the seeded eight. They may be re-permissioned but never
 * deleted — a company with no {@code COMPANY_ADMIN} role has nobody who can administer
 * it, and recovering from that needs support intervention.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "company_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_company_roles_company_code", columnNames = {"company_id", "role_code"}),
        indexes = {
                @Index(name = "idx_company_roles_company", columnList = "company_id, status"),
                @Index(name = "idx_company_roles_type", columnList = "company_id, role_type")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass,
// and an unfiltered company-owned entity is a cross-company data leak.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class CompanyRole extends CompanyOwnedEntity {

    @Column(name = "role_code", nullable = false, updatable = false, length = 50)
    private String roleCode;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(name = "description", length = 255)
    private String description;

    /** Which part of the business this role belongs to. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role_type", nullable = false, length = 20)
    @Builder.Default
    private RoleType roleType = RoleType.OPERATIONS;

    /** Seeded by the platform. Editable, not deletable. */
    @Column(name = "system_role", nullable = false)
    @Builder.Default
    private boolean systemRole = false;

    /**
     * Assigned automatically to a new user when no role is specified. At most one per
     * company — enforced in the service, since MySQL has no partial unique index.
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultRole = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RoleStatus status = RoleStatus.ACTIVE;

    public boolean isActive() {
        return status == RoleStatus.ACTIVE;
    }

    public void activate() {
        this.status = RoleStatus.ACTIVE;
    }

    /**
     * Withdraws the role from the assignment list. Existing holders keep it — a
     * deactivation that silently stripped access from everyone holding the role would
     * be an outage, not a configuration change.
     */
    public void deactivate() {
        this.status = RoleStatus.INACTIVE;
    }

    public void markAsDefault() {
        this.defaultRole = true;
    }

    public void clearDefault() {
        this.defaultRole = false;
    }

    /** Normalises the fields that are matched on. Called before every save. */
    public void applyInvariants() {
        this.roleCode = normaliseCode(roleCode);
        this.roleName = roleName == null ? null : roleName.trim();
        if (this.roleType == null) {
            this.roleType = RoleType.OPERATIONS;
        }
        if (this.status == null) {
            this.status = RoleStatus.ACTIVE;
        }
    }

    public static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase().replace(' ', '_');
    }
}
