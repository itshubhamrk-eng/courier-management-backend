package com.courier.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and verifies JWTs.
 *
 * <p>Claim layout:
 * <pre>
 *   sub    userId
 *   cid    companyId          <- the only trusted source of company binding
 *   email  user email
 *   roles  ["COMPANY_ADMIN"]  <- access tokens only
 *   typ    "access" | "refresh"
 *   jti    token id, used for logout and denylisting
 * </pre>
 *
 * <p>The {@code typ} claim is checked on every parse. Without it a refresh token —
 * which is long-lived — would be accepted as an access token, turning a 7-day
 * credential into a 7-day session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_COMPANY_ID = "cid";

    /**
     * The pre-rename spelling of {@link #CLAIM_COMPANY_ID}. Read, never written: a
     * refresh token minted before the rename is valid for up to seven days, and
     * dropping the fallback would sign every one of those users out mid-session.
     * Delete this once the longest refresh TTL has passed since the deploy.
     */
    private static final String LEGACY_CLAIM_COMPANY_ID = "tid";

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";
    /** Optional — present only when the user is staffed at a branch/hub of their own. */
    private static final String CLAIM_BRANCH_ID = "bid";
    private static final String CLAIM_HUB_ID = "hid";
    /**
     * Company brand, carried the same way as {@link #CLAIM_BRANCH_ID}/{@link #CLAIM_HUB_ID}:
     * a client that only decodes the token (a hard page reload, not an in-app navigation)
     * would otherwise lose it, the same bug class the branch/hub claims were added to avoid.
     * Never trusted for authorisation — display only.
     */
    private static final String CLAIM_COMPANY_NAME = "cnm";
    private static final String CLAIM_COMPANY_LOGO = "clogo";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    /** HS256 requires a key of at least 256 bits; anything shorter is trivially brute-forced. */
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            // Fail fast at startup rather than issue forgeable tokens in production.
            throw new IllegalStateException(
                    "app.jwt.secret must be at least %d bytes (got %d). Set a strong JWT_SECRET."
                            .formatted(MIN_SECRET_BYTES, keyBytes.length));
        }
        // Bean Validation cannot express "positive Duration", so the TTLs are
        // checked here. A zero or negative TTL would mint already-expired tokens.
        requirePositive(properties.getAccessTokenTtl(), "app.jwt.access-token-ttl");
        requirePositive(properties.getRefreshTokenTtl(), "app.jwt.refresh-token-ttl");
        if (properties.getClockSkew().isNegative()) {
            throw new IllegalStateException("app.jwt.clock-skew must not be negative");
        }
        if (properties.getRefreshTokenTtl().compareTo(properties.getAccessTokenTtl()) <= 0) {
            throw new IllegalStateException(
                    "app.jwt.refresh-token-ttl must be longer than access-token-ttl");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT provider initialised (issuer={}, accessTtl={}, refreshTtl={})",
                properties.getIssuer(), properties.getAccessTokenTtl(), properties.getRefreshTokenTtl());
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("%s must be a positive duration (got %s)"
                    .formatted(property, value));
        }
    }

    // ---------------------------------------------------------------- generation

    public String generateAccessToken(UUID userId, UUID companyId, String email, Set<String> roles) {
        return generateAccessToken(userId, companyId, email, roles, null, null, null, null);
    }

    /**
     * @param branchId    the caller's own branch, if staffed at one — carried so a client
     *                    can render "book from my branch" without a second round trip after
     *                    login; never trusted for authorisation, same as every other claim
     * @param hubId       the caller's own hub, if staffed at one
     * @param companyName display name of the signed-in company, for session branding; null
     *                    if unknown
     * @param companyLogo logo URL of the signed-in company, same reason as {@code companyName}
     */
    public String generateAccessToken(UUID userId, UUID companyId, String email, Set<String> roles,
                                       UUID branchId, UUID hubId, String companyName, String companyLogo) {
        Instant now = Instant.now();
        var claims = new java.util.HashMap<String, Object>(Map.of(
                CLAIM_COMPANY_ID, companyId != null ? companyId.toString() : "",
                CLAIM_EMAIL, email,
                CLAIM_ROLES, List.copyOf(roles),
                CLAIM_TYPE, TYPE_ACCESS));
        if (branchId != null) claims.put(CLAIM_BRANCH_ID, branchId.toString());
        if (hubId != null) claims.put(CLAIM_HUB_ID, hubId.toString());
        if (companyName != null && !companyName.isBlank()) claims.put(CLAIM_COMPANY_NAME, companyName);
        if (companyLogo != null && !companyLogo.isBlank()) claims.put(CLAIM_COMPANY_LOGO, companyLogo);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .claims(claims)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Refresh tokens carry no roles or email: they are exchanged for an access
     * token, never used to authorise anything, so extra claims are needless
     * exposure.
     */
    public String generateRefreshToken(UUID userId, UUID companyId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getRefreshTokenTtl())))
                .claim(CLAIM_COMPANY_ID, companyId != null ? companyId.toString() : "")
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    // -------------------------------------------------------------- verification

    /**
     * Verifies signature, expiry and issuer.
     *
     * @return the claims, or empty if the token is missing, malformed, expired or
     *         forged. The caller cannot distinguish these cases by design — the
     *         reason is logged server-side only.
     */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .clockSkewSeconds(properties.getClockSkew().toSeconds())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (ExpiredJwtException e) {
            log.debug("Rejected expired token for subject {}", e.getClaims().getSubject());
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected invalid token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parses an access token into a principal.
     *
     * @return empty if the token is invalid <b>or</b> is a refresh token
     */
    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        return parse(token)
                .filter(claims -> TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class)))
                .map(this::toPrincipal);
    }

    /** @return the claims of a valid refresh token, empty otherwise */
    public Optional<Claims> parseRefreshToken(String token) {
        return parse(token)
                .filter(claims -> TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class)));
    }

    public Duration accessTokenTtl() {
        return properties.getAccessTokenTtl();
    }

    public Duration refreshTokenTtl() {
        return properties.getRefreshTokenTtl();
    }

    /** Remaining lifetime — used to size the denylist entry on logout. */
    public Duration remainingValidity(Claims claims) {
        long millis = claims.getExpiration().getTime() - System.currentTimeMillis();
        return millis > 0 ? Duration.ofMillis(millis) : Duration.ZERO;
    }

    private AuthenticatedUser toPrincipal(Claims claims) {
        String company = companyClaim(claims);
        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                (company == null || company.isBlank()) ? null : UUID.fromString(company),
                claims.get(CLAIM_EMAIL, String.class),
                extractRoles(claims),
                claims.getId());
    }

    /**
     * Reads the company binding, falling back to the pre-rename {@code tid} claim.
     * Both spellings mean the same thing; only {@code cid} is ever written.
     */
    public String companyClaim(Claims claims) {
        String value = claims.get(CLAIM_COMPANY_ID, String.class);
        if (value == null || value.isBlank()) {
            value = claims.get(LEGACY_CLAIM_COMPANY_ID, String.class);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRoles(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        if (raw instanceof List<?> list) {
            return new HashSet<>((List<String>) list);
        }
        return Set.of();
    }
}
