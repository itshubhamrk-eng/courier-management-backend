package com.courier.modules.auth.application;

import com.courier.modules.auth.application.port.CompanyDirectoryPort;
import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserSession;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.UnauthorizedException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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
 *       re-authenticate.</li>
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
            // Replay. Cannot distinguish thief from victim, so end the family.
            handleReuse(stored);
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

    private TokenPair issue(User user,
                            UserSession session,
                            UUID familyId,
                            Duration refreshTtl,
                            String ipAddress,
                            String userAgent) {

        CompanyDirectoryPort.CompanyRef company = companyDirectory.findById(user.getCompanyId()).orElse(null);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getCompanyId(), user.getEmail(), user.roleNames(),
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
     * Destroys a rotation family after a replay and records it loudly — this is a
     * strong signal of credential theft and should page someone if it recurs.
     */
    private void handleReuse(RefreshToken replayed) {
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
