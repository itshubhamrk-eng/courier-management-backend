package com.courier.modules.auth.infrastructure.security;

import com.courier.modules.auth.application.AuthProperties;
import com.courier.modules.auth.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;

/**
 * Adapts {@link User} to Spring Security's {@code UserDetails}.
 *
 * <p>The boolean accessors are what make {@code DaoAuthenticationProvider} throw the
 * right exception, and their ordering inside the provider matters:
 * <ul>
 *   <li>{@link #isAccountNonLocked()} and {@link #isEnabled()} are checked
 *       <em>before</em> the password — a locked or disabled account is rejected
 *       without a credential comparison;</li>
 *   <li>{@link #isCredentialsNonExpired()} is checked <em>after</em> the password
 *       verifies, so password expiry is only revealed to someone who already knows
 *       the password.</li>
 * </ul>
 *
 * @param user       the loaded entity, carried through so the use case need not
 *                   re-query after authentication
 * @param maxPasswordAge expiry policy, zero to disable
 */
public record AuthUserDetails(User user, Duration maxPasswordAge) implements UserDetails {

    public static AuthUserDetails of(User user, AuthProperties properties) {
        return new AuthUserDetails(user, properties.getPassword().getMaxAge());
    }

    public UUID userId() {
        return user.getId();
    }

    public UUID companyId() {
        return user.getCompanyId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> (GrantedAuthority) role::authority)
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    /** Not modelled separately from {@link #isEnabled()}; accounts do not expire. */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** Covers both the administrative LOCKED status and the failed-login lock. */
    @Override
    public boolean isAccountNonLocked() {
        return !user.isTemporarilyLocked()
                && user.getStatus() != com.courier.modules.auth.domain.UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !user.isPasswordExpired(maxPasswordAge);
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus().canAuthenticate();
    }

    @Override
    public String toString() {
        // Never let a principal dump reach a log with the hash in it.
        return "AuthUserDetails(userId=%s, companyId=%s)".formatted(userId(), companyId());
    }
}
