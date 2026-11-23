package com.courier.modules.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSessionTest {

    @Test
    @DisplayName("slideExpiry pushes a live session's expiry forward so active use never hits a fixed deadline")
    void slideExpiryExtendsLiveSession() {
        UserSession session = UserSession.builder()
                .lastSeenAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(5)))
                .build();

        session.slideExpiry(Duration.ofDays(7));

        assertThat(session.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(6)));
        assertThat(session.isActive()).isTrue();
    }

    @Test
    @DisplayName("slideExpiry never shortens a session (e.g. a remember-me session refreshed with a shorter ttl)")
    void slideExpiryNeverShortens() {
        Instant original = Instant.now().plus(Duration.ofDays(30));
        UserSession session = UserSession.builder().lastSeenAt(Instant.now()).expiresAt(original).build();

        session.slideExpiry(Duration.ofDays(7));

        assertThat(session.getExpiresAt()).isEqualTo(original);
    }
}
