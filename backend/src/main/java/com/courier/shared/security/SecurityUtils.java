package com.courier.shared.security;

import com.courier.shared.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Accessors for the current principal.
 *
 * <p>Use this instead of reaching into {@link SecurityContextHolder} at call sites,
 * so that the "anonymous request" case is handled the same way everywhere.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        // Anonymous authentication carries a String principal, not our record.
        return authentication.getPrincipal() instanceof AuthenticatedUser user
                ? Optional.of(user)
                : Optional.empty();
    }

    public static AuthenticatedUser requireCurrentUser() {
        return getCurrentUser().orElseThrow(
                () -> new UnauthorizedException("No authenticated user in the security context"));
    }

    public static Optional<UUID> getCurrentUserId() {
        return getCurrentUser().map(AuthenticatedUser::userId);
    }

    public static boolean isAuthenticated() {
        return getCurrentUser().isPresent();
    }

    public static boolean isPlatformAdmin() {
        return getCurrentUser().map(AuthenticatedUser::isPlatformAdmin).orElse(false);
    }
}
