package com.courier.modules.security.application.scan;

import com.courier.modules.auth.domain.LoginEventType;
import com.courier.modules.auth.domain.LoginHistory;
import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.UserEventCount;
import com.courier.shared.activity.domain.ActivityLog;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.ActivityStatus;
import com.courier.shared.activity.domain.SessionFingerprint;
import com.courier.shared.activity.domain.SessionIpSummary;
import com.courier.shared.config.JpaAuditingConfig;
import com.courier.shared.domain.TimeOrderedUuid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the scanner's custom queries and the V140 cursor table against a real scratch MySQL
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

    @Autowired ActivityLogRepository activityLogs;
    @Autowired LoginHistoryRepository loginHistory;
    @Autowired ScanCursorRepository cursors;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:02:00Z");
    final UUID company = TimeOrderedUuid.generate();

    private ActivityLog log(UUID user, UUID session, String ip, String browser, Instant at) {
        return activityLogs.saveAndFlush(ActivityLog.builder()
                .companyId(company).userId(user).sessionId(session)
                .ipAddress(ip).browser(browser).os("Windows").action("READ")
                .status(ActivityStatus.SUCCESS).occurredAt(at).build());
    }

    @Test
    void fingerprintsAndMultiIpRespectWindowAndDistinctness() {
        UUID user = TimeOrderedUuid.generate();
        UUID session = TimeOrderedUuid.generate();
        log(user, session, "1.1.1.1", "Chrome", from.plusSeconds(5));
        log(user, session, "2.2.2.2", "Firefox", from.plusSeconds(10));
        log(user, session, "3.3.3.3", "Edge", to);                      // end-exclusive
        log(user, session, "4.4.4.4", "Edge", from.minusSeconds(1));    // before window

        List<SessionFingerprint> fps = activityLogs.findSessionFingerprints(from, to).stream()
                .filter(f -> session.equals(f.sessionId())).toList();
        assertThat(fps).extracting(SessionFingerprint::browser).containsExactlyInAnyOrder("Chrome", "Firefox");

        List<SessionIpSummary> multi = activityLogs.findMultiIpSessions(from, to).stream()
                .filter(s -> session.equals(s.sessionId())).toList();
        assertThat(multi).singleElement().satisfies(s -> assertThat(s.distinctIpCount()).isEqualTo(2));
        assertThat(activityLogs.findDistinctIps(session, from, to, PageRequest.of(0, 5)))
                .containsExactly("1.1.1.1", "2.2.2.2");
    }

    @Test
    void singleIpSessionIsNotMultiIp() {
        UUID user = TimeOrderedUuid.generate();
        UUID session = TimeOrderedUuid.generate();
        log(user, session, "1.1.1.1", "Chrome", from.plusSeconds(5));
        log(user, session, "1.1.1.1", "Chrome", from.plusSeconds(6));

        assertThat(activityLogs.findMultiIpSessions(from, to))
                .noneMatch(s -> session.equals(s.sessionId()));
    }

    @Test
    void sessionExpiredCountsAreStrictlyAboveThreshold() {
        UUID user = TimeOrderedUuid.generate();
        for (int i = 0; i < 3; i++) {
            LoginHistory h = LoginHistory.builder().userId(user).attemptedEmail("a@b.c").success(false)
                    .eventType(LoginEventType.SESSION_EXPIRED).occurredAt(from.plusSeconds(i + 1)).build();
            h.setCompanyId(company);
            loginHistory.saveAndFlush(h);
        }

        assertThat(loginHistory.findUsersWithSessionExpiredAbove(from, to, 3))
                .noneMatch(r -> user.equals(r.userId()));
        assertThat(loginHistory.findUsersWithSessionExpiredAbove(from, to, 2))
                .filteredOn(r -> user.equals(r.userId())).singleElement()
                .extracting(UserEventCount::count).isEqualTo(3L);
    }

    @Test
    void cursorRoundTrips() {
        cursors.deleteAll();
        cursors.saveAndFlush(new ScanCursor(ScanCursor.SINGLETON_ID, from));

        assertThat(cursors.findById(ScanCursor.SINGLETON_ID)).get()
                .extracting(ScanCursor::getLastScannedAt).isEqualTo(from);
    }
}
