package com.courier.modules.security.application;

import com.courier.modules.auth.application.SessionService;
import com.courier.modules.auth.application.TokenRevocationService.RevocableTokenStore;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class UserBanServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final RevocableTokenStore store = mock(RevocableTokenStore.class);
    private final ObjectProvider<RevocableTokenStore> storeProvider = mock(ObjectProvider.class);
    private final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    private final AuditService audit = mock(AuditService.class);

    private UserBanService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(storeProvider.getIfAvailable()).thenReturn(store);
        when(jwt.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        service = new UserBanService(userRepository, sessionService, storeProvider, jwt, audit);
    }

    private User userWith(UserStatus status) {
        User user = User.builder().email("a@b.com").status(status).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void banDisablesRevokesSessionsWritesBanKeyWithMarginAndAudits() {
        User user = userWith(UserStatus.ACTIVE);

        service.ban(userId, "reuse");

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        verify(sessionService).revokeAllSessions(eq(userId), any());
        verify(store).banUser(userId, Duration.ofMinutes(20));
        verify(audit).record(eq(AuditAction.USER_BANNED), eq("User"), eq(userId), anyMap());
    }

    @Test
    void banIsIdempotentOnAlreadyDisabledUser() {
        User user = userWith(UserStatus.DISABLED);

        assertThatCode(() -> service.ban(userId, "again")).doesNotThrowAnyException();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void redisFailureDoesNotFailTheBan() {
        User user = userWith(UserStatus.ACTIVE);
        doThrow(new IllegalStateException("redis down")).when(store).banUser(any(), any());

        assertThatCode(() -> service.ban(userId, "x")).doesNotThrowAnyException();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void banWorksWithoutRedis() {
        when(storeProvider.getIfAvailable()).thenReturn(null);
        User user = userWith(UserStatus.ACTIVE);

        service.ban(userId, "x");

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void unbanReactivatesResetsCountersClearsKeyAndAudits() {
        User user = userWith(UserStatus.DISABLED);
        user.setFailedAttempts(7);
        user.setLockedUntil(Instant.now().plusSeconds(600));

        service.unban(userId);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(store).unbanUser(userId);
        verify(audit).record(AuditAction.USER_UNBANNED, "User", userId);
    }
}
