package com.courier.modules.auth.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisTokenRevocationCheckerTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisTokenRevocationChecker checker;
    private final UUID userId = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        checker = new RedisTokenRevocationChecker(redis);
    }

    @Test
    @DisplayName("banUser writes banned-user:<uuid> with the given TTL")
    void banWritesKeyWithTtl() {
        checker.banUser(userId, Duration.ofMinutes(15));
        verify(ops).set("banned-user:" + userId, "banned", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("isUserBanned reflects presence of the key")
    void isUserBannedReflectsKey() {
        when(redis.hasKey("banned-user:" + userId)).thenReturn(true);
        assertThat(checker.isUserBanned(userId)).isTrue();
        when(redis.hasKey("banned-user:" + userId)).thenReturn(false);
        assertThat(checker.isUserBanned(userId)).isFalse();
    }

    @Test
    @DisplayName("unbanUser deletes the key")
    void unbanDeletesKey() {
        checker.unbanUser(userId);
        verify(redis).delete("banned-user:" + userId);
    }

    @Test
    @DisplayName("Redis failure is fail-open: isUserBanned false, ban/unban do not throw")
    void failOpen() {
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("down"));
        when(redis.delete(anyString())).thenThrow(new RuntimeException("down"));
        doThrow(new RuntimeException("down")).when(ops).set(anyString(), anyString(), any(Duration.class));

        assertThat(checker.isUserBanned(userId)).isFalse();
        assertThatCode(() -> checker.banUser(userId, Duration.ofMinutes(15))).doesNotThrowAnyException();
        assertThatCode(() -> checker.unbanUser(userId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null userId or non-positive ttl is ignored")
    void invalidArgsIgnored() {
        assertThat(checker.isUserBanned(null)).isFalse();
        checker.banUser(null, Duration.ofMinutes(1));
        checker.banUser(userId, Duration.ZERO);
        verifyNoInteractions(ops);
    }
}
