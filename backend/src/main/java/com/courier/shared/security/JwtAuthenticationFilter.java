package com.courier.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates a request from its {@code Authorization: Bearer <jwt>} header.
 *
 * <p>Populates the {@link SecurityContextHolder} and nothing else — company binding
 * is {@link com.courier.shared.company.CompanyResolutionFilter}'s job, and it runs
 * next in the chain.
 *
 * <p>An invalid or absent token is <b>not</b> rejected here. The filter simply leaves
 * the context unauthenticated and lets the authorization rules decide, so that public
 * endpoints (login, tracking, Swagger) still work when a stale token is presented.
 *
 * <p>A signature-verified token is additionally checked against
 * {@link AccessTokenRevocationChecker}, so that logout can invalidate an access
 * token before it expires. The checker is optional: without an implementation on
 * the classpath (foundation-only builds) the check is skipped rather than failing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    /**
     * Empty until the auth module contributes an implementation. Optional
     * injection keeps {@code shared} independent of {@code modules}.
     */
    private final Optional<AccessTokenRevocationChecker> revocationChecker;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        extractToken(request)
                .flatMap(tokenProvider::parseAccessToken)
                .filter(this::isNotRevoked)
                .ifPresent(user -> authenticate(user, request));

        filterChain.doFilter(request, response);
    }

    private boolean isNotRevoked(AuthenticatedUser user) {
        if (revocationChecker.isEmpty() || user.tokenId() == null) {
            return true;
        }
        boolean revoked = revocationChecker.get().isRevoked(user.tokenId());
        if (revoked) {
            log.debug("Rejected revoked access token for user {}", user.userId());
        }
        return !revoked;
    }

    private void authenticate(AuthenticatedUser user, HttpServletRequest request) {
        // Credentials are null: the token was already verified, and keeping it in the
        // context would put a live credential into anything that logs the principal.
        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.trace("Authenticated {}", user);
    }

    private java.util.Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(token);
        }
        return java.util.Optional.empty();
    }
}
