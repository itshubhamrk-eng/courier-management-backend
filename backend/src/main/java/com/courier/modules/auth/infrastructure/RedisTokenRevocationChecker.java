package com.courier.modules.auth.infrastructure;

import com.courier.modules.auth.application.TokenRevocationService;
import com.courier.shared.security.AccessTokenRevocationChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed access-token denylist.
 *
 * <p>Implements the read side ({@link AccessTokenRevocationChecker}, defined in
 * {@code shared}) and the write side
 * ({@link TokenRevocationService.RevocableTokenStore}, defined in this module) so
 * the foundation can check revocation without gaining the ability to revoke.
 *
 * <h2>Failure behaviour: fail open, loudly</h2>
 * If Redis is unreachable, {@link #isRevoked} returns {@code false} and logs at
 * ERROR. That is a deliberate availability-over-revocation trade:
 * <ul>
 *   <li>failing closed would reject <em>every</em> authenticated request the moment
 *       the cache blinked — a total outage caused by a cache;</li>
 *   <li>the exposure is bounded by the 15-minute access-token TTL;</li>
 *   <li>refresh tokens are revoked in MySQL regardless, so a revoked session still
 *       cannot be extended past the current access token.</li>
 * </ul>
 * The ERROR log is the alert signal — a sustained stream of these means revocation
 * is not being enforced and Redis needs attention.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenRevocationChecker
        implements AccessTokenRevocationChecker, TokenRevocationService.RevocableTokenStore {

    private static final String KEY_PREFIX = "auth:denylist:";

    private static final String BANNED_USER_PREFIX = "banned-user:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + tokenId));
        } catch (Exception e) {
            log.error("Redis unavailable; access-token revocation is NOT being enforced "
                    + "for jti {}. Tokens remain valid until their natural expiry.", tokenId, e);
            return false;
        }
    }

    @Override
    public boolean isUserBanned(UUID userId) {
        if (userId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BANNED_USER_PREFIX + userId));
        } catch (Exception e) {
            log.error("Redis unavailable; user-level ban is NOT being enforced for user {}.", userId, e);
            return false;
        }
    }

    @Override
    public void banUser(UUID userId, Duration ttl) {
        if (userId == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(BANNED_USER_PREFIX + userId, "banned", ttl);
        } catch (Exception e) {
            log.error("Redis unavailable; could not write user-level ban for {}. Live access "
                    + "tokens stay valid for up to {}.", userId, ttl, e);
        }
    }

    @Override
    public void unbanUser(UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.delete(BANNED_USER_PREFIX + userId);
        } catch (Exception e) {
            log.error("Redis unavailable; could not remove user-level ban for {}. It expires on its own.",
                    userId, e);
        }
    }

    @Override
    public boolean revoke(String tokenId, Duration ttl) {
        if (tokenId == null || tokenId.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + tokenId, "revoked", ttl);
            log.debug("Access token {} denylisted for {}", tokenId, ttl);
            return true;
        } catch (Exception e) {
            // Logout must still succeed: the refresh token and session are already
            // revoked in MySQL, so the session cannot continue past this token.
            log.error("Redis unavailable; could not denylist access token {}. It stays valid "
                    + "for up to {} but the session cannot be refreshed.", tokenId, ttl, e);
            return false;
        }
    }
}
