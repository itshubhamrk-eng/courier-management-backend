package com.courier.modules.security.application.scan;

import com.courier.modules.auth.domain.LoginEventType;
import com.courier.modules.auth.domain.LoginHistory;
import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.SessionRefreshEvent;
import com.courier.modules.auth.domain.UserEventCount;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserSessionRepository;
import com.courier.shared.config.JpaAuditingConfig;
import com.courier.shared.domain.TimeOrderedUuid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the scanner's custom queries and the V140/V141 schema against a real scratch MySQL
 * schema. Gated on {@code SECURITY_IT_DB_URL} exactly like {@code SecurityIncidentRepositoryTest}.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "SECURITY_IT_DB_URL", matches = ".+")
@TestPropertySource(properties = {
        "spring.datasource.url=${SECURITY_IT_DB_URL}",
        "spring.datasource.username=${SECURITY_IT_DB_USER:root}",
        "spring.datasource.password=${SECURITY_IT_DB_PASSWORD:}",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class ScanQueriesRepositoryTest {

    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired LoginHistoryRepository loginHistory;
    @Autowired UserSessionRepository sessions;
    @Autowired ScanCursorRepository cursors;
    @Autowired TestEntityManager em;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:10:00Z");
    final UUID company = TimeOrderedUuid.generate();

    /** refresh_tokens has an FK to users, so FK checks are relaxed for the test transaction. */
    private RefreshToken token(UUID user, UUID session, String ip, String ua, Instant createdAt) {
        RefreshToken t = RefreshToken.builder().userId(user).sessionId(session)
                .familyId(TimeOrderedUuid.generate()).jti(UUID.randomUUID().toString())
                .tokenHash(UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""))
                .expiresAt(createdAt.plusSeconds(3600)).ipAddress(ip).userAgent(ua).build();
        t.setCompanyId(company);
        em.persist(t);
        em.flush();
        // createdAt is stamped by auditing; backdate it so the window test is deterministic.
        em.getEntityManager().createNativeQuery("update refresh_tokens set created_at = ?1 where id = ?2")
                .setParameter(1, java.sql.Timestamp.from(createdAt))
                .setParameter(2, t.getId()).executeUpdate();
        return t;
    }

    private void relaxFks() {
        em.getEntityManager().createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
    }

    @Test
    void refreshEventsRespectHalfOpenWindow() {
        relaxFks();
        UUID user = TimeOrderedUuid.generate();
        UUID session = TimeOrderedUuid.generate();
        token(user, session, "1.1.1.1", "ua-in", from.plusSeconds(5));
        token(user, session, "2.2.2.2", "ua-edge", to);                   // end-exclusive
        token(user, session, "3.3.3.3", "ua-before", from.minusSeconds(1));
        em.clear();

        assertThat(refreshTokens.findSessionRefreshEvents(from, to))
                .filteredOn(e -> session.equals(e.sessionId()))
                .extracting(SessionRefreshEvent::ipAddress)
                .containsExactly("1.1.1.1");
    }

    @Test
    void onlyRevokedSessionsCountTowardReplayAndThresholdIsStrict() {
        relaxFks();
        UUID user = TimeOrderedUuid.generate();
        UUID revoked = sessionRow(user, true);
        UUID expired = sessionRow(user, false);
        for (int i = 0; i < 3; i++) {
            expiredEvent(user, revoked, from.plusSeconds(i + 1));
        }
        UUID other = TimeOrderedUuid.generate();
        for (int i = 0; i < 3; i++) {
            expiredEvent(other, expired, from.plusSeconds(i + 1));
        }
        em.clear();

        assertThat(loginHistory.findUsersWithRevokedSessionRefreshAbove(from, to, 3))
                .noneMatch(r -> user.equals(r.userId()));
        assertThat(loginHistory.findUsersWithRevokedSessionRefreshAbove(from, to, 2))
                .filteredOn(r -> user.equals(r.userId())).singleElement()
                .extracting(UserEventCount::count).isEqualTo(3L);
        // naturally expired (never revoked) session: not counted at all
        assertThat(loginHistory.findUsersWithRevokedSessionRefreshAbove(from, to, 0))
                .noneMatch(r -> other.equals(r.userId()));
    }

    private UUID sessionRow(UUID user, boolean revoked) {
        UserSession s = UserSession.builder().userId(user).lastSeenAt(from)
                .expiresAt(from.plusSeconds(3600)).build();
        s.setCompanyId(company);
        if (revoked) {
            s.revoke("LOGOUT");
        }
        em.persist(s);
        em.flush();
        return s.getId();
    }

    private void expiredEvent(UUID user, UUID session, Instant at) {
        LoginHistory h = LoginHistory.builder().userId(user).attemptedEmail("a@b.c").success(false)
                .eventType(LoginEventType.SESSION_EXPIRED).sessionId(session).occurredAt(at).build();
        h.setCompanyId(company);
        em.persist(h);
        em.flush();
    }

    @Test
    void cursorRoundTrips() {
        cursors.deleteAll();
        cursors.saveAndFlush(new ScanCursor(ScanCursor.SINGLETON_ID, from));

        assertThat(cursors.findById(ScanCursor.SINGLETON_ID)).get()
                .extracting(ScanCursor::getLastScannedAt).isEqualTo(from);
    }
}
