package com.courier.modules.auth.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Wires the authentication provider and manager for the auth module.
 *
 * <p>Kept separate from {@code shared}'s {@code SecurityConfig}, which owns the
 * filter chain and must not gain a dependency on a business module.
 *
 * <p>Using {@code DaoAuthenticationProvider} rather than comparing hashes by hand is
 * a security decision, not a stylistic one: it performs a dummy BCrypt comparison
 * when the user is not found, so response time does not reveal whether an email
 * address is registered.
 */
@Configuration
@RequiredArgsConstructor
public class AuthSecurityConfig {

    @Bean
    public DaoAuthenticationProvider authenticationProvider(AuthUserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Collapse "no such user" into BadCredentialsException so the caller cannot
        // distinguish an unknown email from a wrong password.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider authenticationProvider) {
        return new ProviderManager(List.of(authenticationProvider));
    }
}
