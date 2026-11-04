package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.application.port.UserCompanyRolesPort;
import com.courier.modules.auth.application.port.UserPermissionsPort;
import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityIncidentService;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.UnauthorizedException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mints token pairs and enforces refresh-token rotation with replay detection.
 *
 * <p>The rotation contract, which is the security core of this module:
 * <ol>
 *   <li>Every refresh issues a <b>new</b> pair and revokes the presented token,
 *       recording the successor's {@code jti}.</li>
 *   <li>All tokens descended from one login share a {@code familyId}.</li>
 *   <li>Presenting an <b>already-revoked</b> token means it was replayed — either
 *       the attacker is using a stolen copy or the legitimate client is. We cannot
 *       tell which, so the entire family is destroyed and both parties must
 *       re-authenticate. A <i>rotated</i> token replayed this way also raises a
 *       REFRESH_TOKEN_REUSE security incident (auto-ban), except within the
 *       concurrent-refresh grace window (old or successor token's IP, within
 *       {@code graceSeconds}), which
 *       is rejected without destroying anything.</li>
 * </ol>
 *
 * <p>Point 3 is what bounds the damage of a stolen refresh token: the thief gets at
 * most one use before the theft becomes self-evident and the family dies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final CompanyDirectoryPort companyDirectory;
    private final UserPermissionsPort userPermissionsPort;
    private final UserCompanyRolesPort userCompanyRolesPort;
    private final SecurityIncidentService securityIncidentService;
    private final SecurityProperties securityProperties;
    private final PlatformTransactionManager transactionManager;

    /** A freshly minted pair plus the metadata the caller needs to respond. */
    public record TokenPair(String accessToken,
                            String refreshToken,
                            Duration accessTokenTtl,
                            Duration refreshTokenTtl,
                            UUID sessionId) {
    }

    /**
     * Issues the first pair of a new session, starting a fresh rotation family.
     */
    @Transactional
    public TokenPair issueForNewSession(User user,
                                        UserSession session,
                                        Duration refreshTtl,
                                        String ipAddress,
                                        String userAgent) {
        return issue(user, session, UUID.randomUUID(), refreshTtl, ipAddress, userAgent);
    }

    /**
     * Rotates a presented refresh token.
     *
     * @throws UnauthorizedException if the token is unknown, expired, or already
     *         used — the last case additionally destroys the family
     */
    @Transactional
    public RotationResult rotate(String rawRefreshToken, String ipAddress, String userAgent) {
        Claims claims = jwtTokenProvider.parseRefreshToken(rawRefreshToken)
                .orElseThrow(() -> invalidRefresh("signature, type or expiry rejected"));

        String tokenHash = TokenHasher.hash(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> invalidRefresh("no matching refresh token row"));

        if (stored.isRevoked()) {
            boolean rotated = stored.getReplacedByJti() != null;
            if (rotated && isConcurrentRefresh(stored, ipAddress)) {
                // Two tabs (or a retry) refreshed the same token moments apart from the
                // same place. Reject the loser, but this is not theft: keep the family.
                throw invalidRefresh("concurrent refresh within grace window");
            }
            // Replay. Cannot distinguish thief from victim, so end the family.
            handleReuse(stored);
            if (rotated) {
                // Only a rotated token being presented again is a reuse signal; a token
                // revoked by logout/eviction (no successor) is just stale.
                raiseReuseIncident(stored, ipAddress, userAgent);
            }
            throw invalidRefresh("refresh token replay detected");
        }
        if (stored.isExpired()) {
            stored.revoke("EXPIRED");
            refreshTokenRepository.save(stored);
            throw invalidRefresh("refresh token expired");
        }

        return new RotationResult(stored, UUID.fromString(claims.getSubject()));
    }

    /**
     * Completes a rotation: revokes the old row and issues the successor inside the
     * same family.
     */
    @Transactional
    public TokenPair completeRotation(User user,
                                      UserSession session,
                                      RefreshToken presented,
                                      Duration refreshTtl,
                                      String ipAddress,
                                      String userAgent) {

        TokenPair pair = issue(user, session, presented.getFamilyId(), refreshTtl, ipAddress, userAgent);

        // Link old -> new so a later replay can be traced to its successor.
        String successorJti = jwtTokenProvider.parseRefreshToken(pair.refreshToken())
                .map(Claims::getId)
                .orElse(null);
        presented.rotateTo(successorJti);
        refreshTokenRepository.save(presented);

        auditService.record(AuditAction.TOKEN_REFRESHED, "RefreshToken", presented.getId(),
                Map.of("userId", user.getId().toString(),
                        "sessionId", session.getId().toString(),
                        "familyId", presented.getFamilyId().toString()));
        return pair;
    }

    /**
     * The roles a session for {@code user} carries: the legacy JWT-authority {@code Role} enum
     * plus any role granted through Role/Permission Management ({@code user_company_roles}).
     * One definition for normal login and impersonation, so an impersonated session sees
     * exactly the menu the real login would (e.g. a hub account's {@code HUB_MANAGER}).
     */
    public Set<String> effectiveRoles(User user) {
        Set<String> roles = new HashSet<>(user.roleNames());
        roles.addAll(userCompanyRolesPort.resolveRoleCodes(user.getId()));
        return roles;
    }

    private TokenPair issue(User user,
                            UserSession session,
                            UUID familyId,
                            Duration refreshTtl,
                            String ipAddress,
                            String userAgent) {

        CompanyDirectoryPort.CompanyRef company = companyDirectory.findById(user.getCompanyId()).orElse(null);
        // Union: the legacy JWT-authority Role enum (user_roles) plus any role granted
        // through Role/Permission Management (user_company_roles) — see UserCompanyRolesPort.
        Set<String> roles = effectiveRoles(user);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getCompanyId(), user.getEmail(), roles,
                userPermissionsPort.resolveEffectivePermissions(user.getId(), roles),
                user.getBranchId(), user.getHubId(),
                company != null ? company.name() : null, company != null ? company.logo() : null);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getCompanyId());

        String jti = jwtTokenProvider.parseRefreshToken(refreshToken)
                .map(Claims::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Freshly minted refresh token failed to parse — check JwtTokenProvider"));

        RefreshToken row = RefreshToken.builder()
                .userId(user.getId())
                .sessionId(session.getId())
                .familyId(familyId)
                .jti(jti)
                // Only the hash is persisted: a leaked table must not yield live tokens.
                .tokenHash(TokenHasher.hash(refreshToken))
                .expiresAt(Instant.now().plus(refreshTtl))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        refreshTokenRepository.save(row);

        return new TokenPair(accessToken, refreshToken,
                jwtTokenProvider.accessTokenTtl(), refreshTtl, session.getId());
    }

    /**
     * A rotated token presented again within {@code graceSeconds} of its rotation, by
     * the same client, is a concurrent refresh (parallel tabs/requests, a retry) rather
     * than theft. "Same client" means the presenting IP equals either the IP the old
     * token was issued to, or the IP of the successor token, i.e. the request that won
     * the rotation, since the user's IP may have changed since the old token was issued
     * (mobile data, CGNAT). If the successor row is missing, only the old token's IP counts.
     *
     * <p>The session half of "same session" holds by construction: a refresh request
     * carries no session identifier of its own, the session is the presented token's
     * {@code sessionId}, and rotation always mints the successor into that same session.
     * An unknown IP never matches, and a negative elapsed time (revokedAt in the future,
     * clock skew) is outside grace, so neither can buy the grace window.
     */
    private boolean isConcurrentRefresh(RefreshToken stored, String ipAddress) {
        Duration sinceRotation = Duration.between(stored.getRevokedAt(), Instant.now());
        if (ipAddress == null
                || sinceRotation.isNegative()
                || sinceRotation.compareTo(Duration.ofSeconds(securityProperties.getGraceSeconds())) > 0) {
            return false;
        }
        if (ipAddress.equals(stored.getIpAddress())) {
            return true;
        }
        return refreshTokenRepository.findByJti(stored.getReplacedByJti())
                .map(successor -> ipAddress.equals(successor.getIpAddress()))
                .orElse(false);
    }

    /**
     * Raises REFRESH_TOKEN_REUSE (which auto-bans the user). Called only after
     * {@link #handleReuse} has committed its own transaction, and while the surrounding
     * refresh transaction holds no writes or locks for this user (rotate has only done
     * non-locking reads so far), so the REQUIRES_NEW raise never waits on our locks.
     * A failure here is logged and swallowed: the client must see the same
     * INVALID_REFRESH_TOKEN either way.
     */
    private void raiseReuseIncident(RefreshToken stored, String ipAddress, String userAgent) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("familyId", stored.getFamilyId().toString());
        evidence.put("sessionId", stored.getSessionId().toString());
        evidence.put("presentedFromIp", ipAddress);
        evidence.put("originalIp", stored.getIpAddress());
        evidence.put("userAgent", userAgent);
        evidence.put("originalUserAgent", stored.getUserAgent());
        evidence.put("revokedAt", String.valueOf(stored.getRevokedAt()));
        try {
            securityIncidentService.raise(new IncidentRequest(
                    stored.getCompanyId(), stored.getUserId(), IncidentRule.REFRESH_TOKEN_REUSE, evidence));
        } catch (RuntimeException e) {
            log.error("Could not raise REFRESH_TOKEN_REUSE incident for user {} (family {}): {}",
                    stored.getUserId(), stored.getFamilyId(), e.toString());
        }
    }

    /**
     * Destroys a rotation family after a replay and records it loudly — this is a
     * strong signal of credential theft and should page someone if it recurs.
     *
     * <p>Runs in its own REQUIRES_NEW transaction that commits before we return.
     * {@code rotate} is called inside {@code AuthService.refresh}'s transaction and
     * then throws INVALID_REFRESH_TOKEN, which rolls that transaction back, so a revoke
     * written there would be undone. Committing it separately keeps the family dead and
     * also means the follow-up incident raise (whose ban revokes this user's refresh
     * tokens) never meets uncommitted family-revoke row locks.
     */
    private void handleReuse(RefreshToken replayed) {
        TransactionTemplate ownTx = new TransactionTemplate(transactionManager);
        ownTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ownTx.executeWithoutResult(status -> revokeFamilyAndAudit(replayed));
    }

    private void revokeFamilyAndAudit(RefreshToken replayed) {
        int revoked = refreshTokenRepository.revokeFamily(
                replayed.getFamilyId(), RefreshToken.RevokeReason.REUSE_DETECTED, Instant.now());

        log.error("REFRESH TOKEN REPLAY: family {} for user {} revoked ({} token(s)); "
                        + "original token was already revoked at {} with reason {}",
                replayed.getFamilyId(), replayed.getUserId(), revoked,
                replayed.getRevokedAt(), replayed.getRevokedReason());

        auditService.record(AuditAction.REFRESH_TOKEN_REUSE_DETECTED, "RefreshToken", replayed.getId(),
                Map.of("userId", replayed.getUserId().toString(),
                        "familyId", replayed.getFamilyId().toString(),
                        "tokensRevoked", revoked,
                        "originalRevokedReason",
                        Optional.ofNullable(replayed.getRevokedReason()).orElse("unknown")),
                false);
    }

    private UnauthorizedException invalidRefresh(String internalReason) {
        // The caller always sees the same generic message; the specific cause is
        // logged and audited only, so a probe cannot map out token state.
        log.debug("Refresh rejected: {}", internalReason);
        return new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN,
                "Refresh token is invalid or has already been used");
    }

    /** The validated row plus the subject extracted from the token. */
    public record RotationResult(RefreshToken storedToken, UUID userId) {
    }
}
