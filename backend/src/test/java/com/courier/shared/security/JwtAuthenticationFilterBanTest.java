package com.courier.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterBanTest {

    private final JwtTokenProvider provider = mock(JwtTokenProvider.class);
    private final AccessTokenRevocationChecker checker = mock(AccessTokenRevocationChecker.class);
    private final UUID userId = UUID.randomUUID();
    private final AuthenticatedUser user =
            new AuthenticatedUser(userId, UUID.randomUUID(), "a@b.test", Set.of("OPS"), "jti-1");

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(provider.parseAccessToken("tok")).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void run(Optional<AccessTokenRevocationChecker> c) throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        new JwtAuthenticationFilter(provider, c)
                .doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    @DisplayName("banned user's otherwise valid token does not authenticate")
    void bannedUserRejected() throws Exception {
        when(checker.isRevoked("jti-1")).thenReturn(false);
        when(checker.isUserBanned(userId)).thenReturn(true);
        run(Optional.of(checker));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("non-banned, non-revoked user authenticates")
    void okUserAuthenticates() throws Exception {
        run(Optional.of(checker));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("no checker bean: allowed")
    void noCheckerAllowed() throws Exception {
        run(Optional.empty());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
