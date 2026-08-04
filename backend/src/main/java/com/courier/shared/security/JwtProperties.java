package com.courier.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings, bound from {@code app.jwt.*}.
 *
 * <p>The secret has no default: the application must not be able to start with a
 * signing key that happens to be in the source tree. Length is enforced at startup
 * by {@link JwtTokenProvider}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HMAC signing secret. Supplied via the JWT_SECRET environment variable. */
    @NotBlank
    private String secret;

    /** Token issuer claim. */
    @NotBlank
    private String issuer = "courier-management";

    /**
     * Short by design — revocation latency is bounded by this.
     *
     * <p>Only {@code @NotNull} here: Bean Validation has no {@code @Positive}
     * validator for {@link Duration}, and annotating it that way fails at startup
     * with {@code HV000030}. Positivity is asserted in {@link JwtTokenProvider#init()}.
     */
    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Rotated on every use; reuse of a spent token revokes the whole family. */
    @NotNull
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** Tolerance for clock drift between the issuing and verifying nodes. */
    @NotNull
    private Duration clockSkew = Duration.ofSeconds(30);
}
