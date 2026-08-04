package com.courier.shared.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The token layer is the only thing standing between a request and another
 * company's data, so its failure modes are tested explicitly rather than assumed.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-32b!";

    private JwtTokenProvider provider;
    private UUID userId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(properties(SECRET));
        provider.init();
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }

    private JwtProperties properties(String secret) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        props.setIssuer("courier-management");
        props.setAccessTokenTtl(Duration.ofMinutes(15));
        props.setRefreshTokenTtl(Duration.ofDays(7));
        props.setClockSkew(Duration.ofSeconds(30));
        return props;
    }

    @Test
    @DisplayName("access token round-trips every claim the principal needs")
    void accessTokenRoundTrip() {
        String token = provider.generateAccessToken(
                userId, companyId, "ops@acme.test", Set.of(Roles.COMPANY_ADMIN, Roles.OPERATOR));

        AuthenticatedUser user = provider.parseAccessToken(token).orElseThrow();

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.companyId()).isEqualTo(companyId);
        assertThat(user.email()).isEqualTo("ops@acme.test");
        assertThat(user.roles()).containsExactlyInAnyOrder(Roles.COMPANY_ADMIN, Roles.OPERATOR);
        assertThat(user.tokenId()).isNotBlank();
    }

    @Test
    @DisplayName("authorities carry the ROLE_ prefix Spring Security expects")
    void authoritiesArePrefixed() {
        String token = provider.generateAccessToken(userId, companyId, "a@b.test", Set.of(Roles.OPERATOR));
        AuthenticatedUser user = provider.parseAccessToken(token).orElseThrow();

        assertThat(user.authorities())
                .extracting("authority")
                .containsExactly(Roles.AUTH_OPERATOR);
    }

    @Test
    @DisplayName("a refresh token is rejected where an access token is required")
    void refreshTokenIsNotAcceptedAsAccessToken() {
        // Without the typ check a 7-day refresh credential would authenticate
        // requests for 7 days.
        String refresh = provider.generateRefreshToken(userId, companyId);

        assertThat(provider.parseAccessToken(refresh)).isEmpty();
        assertThat(provider.parseRefreshToken(refresh)).isPresent();
    }

    @Test
    @DisplayName("an access token is rejected where a refresh token is required")
    void accessTokenIsNotAcceptedAsRefreshToken() {
        String access = provider.generateAccessToken(userId, companyId, "a@b.test", Set.of());

        assertThat(provider.parseRefreshToken(access)).isEmpty();
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void forgedSignatureIsRejected() {
        JwtTokenProvider attacker = new JwtTokenProvider(
                properties("a-completely-different-secret-key-32bytes!"));
        attacker.init();

        String forged = attacker.generateAccessToken(userId, companyId, "evil@attacker.test",
                Set.of(Roles.PLATFORM_ADMIN));

        assertThat(provider.parseAccessToken(forged)).isEmpty();
    }

    @Test
    @DisplayName("a tampered payload is rejected")
    void tamperedTokenIsRejected() {
        String token = provider.generateAccessToken(userId, companyId, "a@b.test", Set.of(Roles.OPERATOR));

        // Flip a character in the payload segment; the signature no longer matches.
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "XY." + parts[2];

        assertThat(provider.parseAccessToken(tampered)).isEmpty();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() {
        JwtProperties expiring = properties(SECRET);
        expiring.setAccessTokenTtl(Duration.ofSeconds(1));
        expiring.setClockSkew(Duration.ZERO);
        JwtTokenProvider shortLived = new JwtTokenProvider(expiring);
        shortLived.init();

        String token = shortLived.generateAccessToken(userId, companyId, "a@b.test", Set.of());

        // The token is valid now...
        assertThat(shortLived.parseAccessToken(token)).isPresent();

        // ...and must not be once its expiry has passed.
        await(1100);
        assertThat(shortLived.parseAccessToken(token)).isEmpty();
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void wrongIssuerIsRejected() {
        JwtProperties other = properties(SECRET);
        other.setIssuer("some-other-service");
        JwtTokenProvider otherIssuer = new JwtTokenProvider(other);
        otherIssuer.init();

        String token = otherIssuer.generateAccessToken(userId, companyId, "a@b.test", Set.of());

        assertThat(provider.parseAccessToken(token)).isEmpty();
    }

    @Test
    @DisplayName("garbage input is rejected without throwing")
    void malformedTokenIsRejected() {
        assertThat(provider.parseAccessToken("not-a-jwt")).isEmpty();
        assertThat(provider.parseAccessToken("")).isEmpty();
        assertThat(provider.parseAccessToken("a.b.c")).isEmpty();
    }

    @Test
    @DisplayName("a weak signing secret stops the application at startup")
    void weakSecretFailsFast() {
        JwtTokenProvider weak = new JwtTokenProvider(properties("too-short"));

        assertThatThrownBy(weak::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("refresh tokens carry no roles or email")
    void refreshTokenIsMinimal() {
        String refresh = provider.generateRefreshToken(userId, companyId);
        Optional<Claims> claims = provider.parseRefreshToken(refresh);

        assertThat(claims).isPresent();
        assertThat(claims.get().get("roles")).isNull();
        assertThat(claims.get().get("email")).isNull();
        assertThat(claims.get().getSubject()).isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("remaining validity shrinks toward the expiry")
    void remainingValidityIsBounded() {
        String refresh = provider.generateRefreshToken(userId, companyId);
        Claims claims = provider.parseRefreshToken(refresh).orElseThrow();

        Duration remaining = provider.remainingValidity(claims);

        assertThat(remaining).isLessThanOrEqualTo(Duration.ofDays(7));
        assertThat(remaining).isGreaterThan(Duration.ofDays(6));
    }

    private static void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
