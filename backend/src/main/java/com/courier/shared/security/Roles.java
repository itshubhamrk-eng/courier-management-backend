package com.courier.shared.security;

/**
 * Role name constants.
 *
 * <p>Spring Security's {@code hasRole()} implicitly prefixes {@code ROLE_}, while
 * {@code hasAuthority()} does not. Both spellings are declared here so that
 * {@code @PreAuthorize} expressions — which are strings the compiler cannot check —
 * are written against constants instead of literals. A typo in a security
 * expression fails open, so this is not cosmetic.
 */
public final class Roles {

    /**
     * Highest tier. Owns platform-wide configuration that no company may touch —
     * currently the subscription plan catalogue. Distinct from
     * {@link #PLATFORM_ADMIN}, which operates <em>on behalf of</em> companies
     * (impersonation, company lifecycle) rather than on the platform itself.
     */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /**
     * Owner of one company. This is the role the first user of a new company receives.
     *
     * <p>{@code TENANT_ADMIN} was the older name for exactly this role and has been
     * removed: a company <em>is</em> the tenant, so two names for one thing could only
     * drift. {@code V12} rewrites the rows that carried it.
     */
    public static final String COMPANY_ADMIN = "COMPANY_ADMIN";

    public static final String BRANCH_MANAGER = "BRANCH_MANAGER";
    public static final String HUB_MANAGER = "HUB_MANAGER";
    public static final String OPERATOR = "OPERATOR";
    public static final String DRIVER = "DRIVER";
    public static final String CUSTOMER = "CUSTOMER";

    /** Read-only access for auditors, finance and support. */
    public static final String VIEWER = "VIEWER";

    public static final String ROLE_PREFIX = "ROLE_";

    /** Authority form, as stored in the SecurityContext. */
    public static final String AUTH_SUPER_ADMIN = ROLE_PREFIX + SUPER_ADMIN;
    public static final String AUTH_PLATFORM_ADMIN = ROLE_PREFIX + PLATFORM_ADMIN;
    public static final String AUTH_COMPANY_ADMIN = ROLE_PREFIX + COMPANY_ADMIN;
    public static final String AUTH_BRANCH_MANAGER = ROLE_PREFIX + BRANCH_MANAGER;
    public static final String AUTH_HUB_MANAGER = ROLE_PREFIX + HUB_MANAGER;
    public static final String AUTH_OPERATOR = ROLE_PREFIX + OPERATOR;
    public static final String AUTH_DRIVER = ROLE_PREFIX + DRIVER;
    public static final String AUTH_CUSTOMER = ROLE_PREFIX + CUSTOMER;
    public static final String AUTH_VIEWER = ROLE_PREFIX + VIEWER;

    private Roles() {
    }
}
