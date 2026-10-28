package com.courier.modules.platformintegration.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSecurityConfigTest {

    private static final PlatformIntegrationProperties PROPS = new PlatformIntegrationProperties(
            true, "https://auth.test/realms/occpl", "https://auth.test/jwks", "courier-api",
            "occpl-core-services", "STARTER", null,
            "courier-web", "http://core", "http://token", "courier-service", "secret");

    private static Jwt token(String issuer, List<String> aud, String clientId) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("s")
                .issuedAt(Instant.now().minusSeconds(10)).expiresAt(Instant.now().plusSeconds(300))
                .issuer(issuer).audience(aud);
        if (clientId != null) {
            b.claim("client_id", clientId);
        }
        return b.build();
    }

    @Test
    void acceptsTokenForThisProductFromTheRealm() {
        var jwt = token("https://auth.test/realms/occpl", List.of("courier-api"), "occpl-core-services");
        assertThat(PlatformSecurityConfig.validator(PROPS).validate(jwt).hasErrors()).isFalse();
        assertThat(PlatformSecurityConfig.authorities(jwt, PROPS))
                .extracting(Object::toString).containsExactly(PlatformSecurityConfig.PROVISIONER);
    }

    @Test
    void rejectsWrongAudience() {
        var jwt = token("https://auth.test/realms/occpl", List.of("other-product"), "occpl-core-services");
        assertThat(PlatformSecurityConfig.validator(PROPS).validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsWrongIssuer() {
        var jwt = token("https://evil.test/realms/occpl", List.of("courier-api"), "occpl-core-services");
        assertThat(PlatformSecurityConfig.validator(PROPS).validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void endUserOrOtherClientGetsNoProvisioningAuthority() {
        var user = token("https://auth.test/realms/occpl", List.of("courier-api"), null);
        var other = token("https://auth.test/realms/occpl", List.of("courier-api"), "some-other-client");
        assertThat(PlatformSecurityConfig.authorities(user, PROPS)).isEmpty();
        assertThat(PlatformSecurityConfig.authorities(other, PROPS)).isEmpty();
    }
}
