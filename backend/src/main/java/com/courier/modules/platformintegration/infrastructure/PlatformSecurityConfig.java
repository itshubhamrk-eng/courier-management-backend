package com.courier.modules.platformintegration.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * A second, narrow security chain for {@code /provisioning/v1/**}.
 *
 * <p>It matches only that path and runs before the app's own chain, so login, the
 * app's HS256 tokens and every existing endpoint are untouched. The caller must hold a
 * platform token that is signed by the realm, unexpired, issued by the configured
 * issuer, addressed to this product, and minted for the platform's service client.
 */
@Configuration
@EnableConfigurationProperties(PlatformIntegrationProperties.class)
@ConditionalOnProperty(prefix = "occpl.platform", name = "enabled", havingValue = "true")
public class PlatformSecurityConfig {

    public static final String PROVISIONER = "PLATFORM_PROVISIONER";

    /** Verifies platform (Keycloak) tokens; also used by the sign-in exchange. */
    @Bean
    JwtDecoder platformJwtDecoder(PlatformIntegrationProperties props) {
        return decoderFor(props);
    }

    /**
     * The sign-in exchange carries the platform token in the request body and checks it itself, so this
     * chain only turns off the app's own bearer-token handling for that one path.
     */
    @Bean
    @Order(2)
    SecurityFilterChain platformSignInChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/auth/platform/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable());
        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain platformProvisioningChain(HttpSecurity http, PlatformIntegrationProperties props,
            JwtDecoder platformJwtDecoder) throws Exception {
        JwtDecoder decoder = platformJwtDecoder;
        http.securityMatcher("/provisioning/v1/**")
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority(PROVISIONER))
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt
                        .decoder(decoder)
                        .jwtAuthenticationConverter(token -> new JwtAuthenticationToken(
                                token, authorities(token, props), token.getSubject()))))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable());
        return http.build();
    }

    static List<SimpleGrantedAuthority> authorities(Jwt token, PlatformIntegrationProperties props) {
        String client = token.getClaimAsString("client_id");
        return client != null && client.equals(props.provisioningClientId())
                ? List.of(new SimpleGrantedAuthority(PROVISIONER))
                : List.of();
    }

    static JwtDecoder decoderFor(PlatformIntegrationProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();
        decoder.setJwtValidator(validator(props));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validator(PlatformIntegrationProperties props) {
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains(props.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token is not addressed to this product", null));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(props.issuerUri()), audience);
    }
}
