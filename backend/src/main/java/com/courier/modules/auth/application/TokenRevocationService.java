package com.courier.modules.auth.application;

import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.security.AccessTokenRevocationChecker;
import com.courier.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Immediate revocation of stateless access tokens.
 *
 * <p>An access token cannot be "deleted" — it is self-contained and signed. The only
 * way to stop one early is a denylist consulted on every request, which is why this
 * lives in Redis rather than MySQL: it sits on the hot path.
 *
 * <p>Entries expire with the token, so the denylist stays bounded at roughly
 * "tokens revoked in the last 15 minutes" and needs no cleanup job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final AccessTokenRevocationChecker revocationChecker;
    private final AuditService auditService;

    /**
     * Denylists the access token the caller is currently authenticated with.
     *
     * <p>Best-effort by design: if Redis is unreachable the token stays valid for
     * the remainder of its short TTL. The refresh token is revoked in MySQL
     * regardless, so the session still cannot be extended. Failing the logout
     * request instead would leave the user believing they are still signed in.
     */
    public void revokeCurrentAccessToken(AuthenticatedUser principal) {
        if (principal.tokenId() == null) {
            return;
        }
        if (!(revocationChecker instanceof RevocableTokenStore store)) {
            log.debug("No revocable token store configured; access token {} will expire naturally",
                    principal.tokenId());
            return;
        }

        boolean revoked = store.revoke(principal.tokenId(), remainingTtlFor(principal));
        if (revoked) {
            auditService.record(AuditAction.TOKEN_REVOKED, "User", principal.userId(),
                    Map.of("jti", principal.tokenId()));
        }
    }

    /**
     * The principal carries no expiry, so the full access TTL is used as an upper
     * bound. Over-estimating only keeps a dead entry slightly longer; under-estimating
     * would let a revoked token come back to life.
     */
    private Duration remainingTtlFor(AuthenticatedUser principal) {
        return Duration.ofMinutes(20);
    }

    /**
     * Write side of the denylist. Kept separate from
     * {@link AccessTokenRevocationChecker} — which lives in {@code shared} and is
     * read-only — so the foundation never gains the ability to revoke.
     */
    public interface RevocableTokenStore {
        boolean revoke(String tokenId, Duration ttl);
    }
}
