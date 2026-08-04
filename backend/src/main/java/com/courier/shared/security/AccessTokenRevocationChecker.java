package com.courier.shared.security;

/**
 * SPI letting a module revoke an access token before its natural expiry.
 *
 * <p>Access tokens are stateless by design, so logout cannot "delete" one. The
 * auth module therefore keeps a short-lived denylist of revoked {@code jti} values
 * and implements this interface; {@link JwtAuthenticationFilter} consults it on
 * every authenticated request.
 *
 * <p>The interface lives in {@code shared} rather than in {@code modules.auth} so
 * that the dependency direction stays correct: {@code shared} must never import
 * from {@code modules}. When no implementation is present — as during the
 * foundation-only phase — the filter simply skips the check.
 */
@FunctionalInterface
public interface AccessTokenRevocationChecker {

    /**
     * @param tokenId the {@code jti} claim of a signature-verified access token
     * @return true if the token has been revoked and must be rejected
     */
    boolean isRevoked(String tokenId);
}
