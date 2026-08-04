package com.courier.shared.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal, built entirely from verified JWT claims.
 *
 * <p>There is intentionally no database lookup behind this: the token is signed,
 * short-lived (15 minutes) and self-describing, so a per-request user load would buy
 * nothing but latency. The cost is that a role change or account disable only takes
 * effect at the next token refresh — an accepted trade recorded in
 * {@code MEMORY/AI_CONTEXT.md}. Immediate revocation is handled by the Redis
 * denylist, which the auth module will wire into the filter.
 *
 * @param userId   subject of the token
 * @param companyId owning company — the sole source of truth for company binding
 * @param email    for logging and {@code /auth/me}
 * @param roles    role names without the {@code ROLE_} prefix
 * @param tokenId  the token's {@code jti}, needed for logout/denylisting
 */
public record AuthenticatedUser(
        UUID userId,
        UUID companyId,
        String email,
        Set<String> roles,
        String tokenId
) {

    public Collection<? extends GrantedAuthority> authorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(Roles.ROLE_PREFIX + role))
                .toList();
    }

    public boolean isPlatformAdmin() {
        return roles.contains(Roles.PLATFORM_ADMIN);
    }

    /**
     * Highest tier: owns platform-wide configuration such as the subscription plan
     * catalogue. Deliberately <em>not</em> folded into {@link #isPlatformAdmin()} —
     * the two grant different things, and a super admin does not inherit company
     * impersonation.
     */
    public boolean isSuperAdmin() {
        return roles.contains(Roles.SUPER_ADMIN);
    }

    /** True for any role that operates outside a single company. */
    public boolean isPlatformTier() {
        return isSuperAdmin() || isPlatformAdmin();
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        // Never log the token id or the full claim set.
        return "AuthenticatedUser(userId=%s, companyId=%s)".formatted(userId, companyId);
    }
}
