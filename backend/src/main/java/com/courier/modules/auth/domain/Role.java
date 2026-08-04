package com.courier.modules.auth.domain;

import com.courier.shared.security.Roles;

/**
 * Roles a user may hold, mirroring the constants in {@link Roles}.
 *
 * <p>The enum is the persisted, type-safe form; {@code Roles} holds the string
 * constants that {@code @PreAuthorize} expressions need, because SpEL cannot
 * reference an enum constant concisely. {@link #assertConsistentWithRolesConstants()}
 * is exercised by a unit test so the two can never drift apart unnoticed.
 */
public enum Role {

    SUPER_ADMIN(Roles.SUPER_ADMIN),
    PLATFORM_ADMIN(Roles.PLATFORM_ADMIN),
    COMPANY_ADMIN(Roles.COMPANY_ADMIN),
    BRANCH_MANAGER(Roles.BRANCH_MANAGER),
    HUB_MANAGER(Roles.HUB_MANAGER),
    OPERATOR(Roles.OPERATOR),
    DRIVER(Roles.DRIVER),
    CUSTOMER(Roles.CUSTOMER),
    VIEWER(Roles.VIEWER);

    private final String constant;

    Role(String constant) {
        this.constant = constant;
    }

    /** Authority form stored in the SecurityContext, e.g. {@code ROLE_OPERATOR}. */
    public String authority() {
        return Roles.ROLE_PREFIX + name();
    }

    /**
     * Platform-tier roles live outside any company and may only be granted by a
     * platform-tier actor. Enforced wherever roles are assigned so a company admin
     * cannot escalate out of their own company.
     */
    public boolean isPlatformScoped() {
        return this == PLATFORM_ADMIN || this == SUPER_ADMIN;
    }

    static void assertConsistentWithRolesConstants() {
        for (Role role : values()) {
            if (!role.name().equals(role.constant)) {
                throw new IllegalStateException(
                        "Role." + role.name() + " does not match Roles." + role.constant);
            }
        }
    }
}
