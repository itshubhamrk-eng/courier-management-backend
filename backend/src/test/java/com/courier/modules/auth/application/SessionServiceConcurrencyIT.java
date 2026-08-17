package com.courier.modules.auth.application;

import com.courier.CourierApplication;
import com.courier.modules.auth.domain.UserSession;
import com.courier.modules.auth.domain.UserSessionRepository;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.domain.TimeOrderedUuid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-concurrency regression test for ISSUE-008 (see {@code perf-tests/ISSUES.md}):
 * fires genuinely simultaneous logins at one account and confirms the session cap
 * ({@link SessionService#openSession}) actually holds, plus that the per-user named
 * lock does not serialize unrelated users against each other.
 *
 * <p>Deliberately an "IT", not a "Test" — Surefire's default include pattern is
 * {@code **&#47;*Test.java} and does not pick this up, so the fast, fully-mocked
 * {@code mvn test} suite used everywhere else in this project is untouched. This
 * test needs a real, reachable MySQL with the standard {@code COMPANY-C1} dev
 * fixture (see {@code MEMORY/dev-login-credential.md}) — the same local database
 * every other part of this project's perf testing already uses. Run explicitly:
 * <pre>
 * DB_USERNAME=root DB_PASSWORD=... mvn -Dtest=SessionServiceConcurrencyIT test
 * </pre>
 */
@SpringBootTest(classes = CourierApplication.class)
class SessionServiceConcurrencyIT {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private DataSource dataSource;

    @Test
    void concurrentLoginsNeverExceedTheSessionCap() throws Exception {
        int cap = authProperties.getMaxConcurrentSessions();
        assertThat(cap).as("app.auth.max-concurrent-sessions").isEqualTo(5);

        UUID companyId = resolveCompanyC1();
        UUID userA = createTestUser(companyId, "sesscap-a");
        UUID userB = createTestUser(companyId, "sesscap-b");

        CompanyContext.setCompanyId(companyId);
        try {
            // Start at 4 active sessions, oldest first, so eviction order is checkable.
            List<UUID> preExisting = seedActiveSessions(userA, 4);
            UUID oldestPreExisting = preExisting.get(0);

            // --- Part 1: cap holds under genuine concurrency ---
            int concurrentLogins = 10;
            ExecutorService pool = Executors.newFixedThreadPool(concurrentLogins);
            CountDownLatch ready = new CountDownLatch(concurrentLogins);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger failures = new AtomicInteger();
            try {
                for (int i = 0; i < concurrentLogins; i++) {
                    int idx = i;
                    pool.submit(() -> {
                        CompanyContext.setCompanyId(companyId);
                        try {
                            ready.countDown();
                            go.await(10, TimeUnit.SECONDS);
                            sessionService.openSession(userA, Duration.ofDays(1), false,
                                    SessionService.DeviceInfo.of("device-" + idx, "Test Device " + idx,
                                            "127.0.0.1", "junit"));
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        } finally {
                            CompanyContext.clear();
                        }
                    });
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).as("all threads reached the barrier").isTrue();
                go.countDown();
            } finally {
                pool.shutdown();
                assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("all logins finished").isTrue();
            }

            assertThat(failures.get()).as("no login should hard-fail because of cap enforcement").isZero();

            List<UserSession> activeAfter = sessionRepository.findActiveByUserId(userA, Instant.now());
            assertThat(activeAfter).as("active sessions must never exceed the cap").hasSizeLessThanOrEqualTo(cap);
            assertThat(activeAfter).as("cap enforcement should land exactly at capacity, not under it")
                    .hasSize(cap);

            UserSession oldest = sessionRepository.findById(oldestPreExisting).orElseThrow();
            assertThat(oldest.getRevokedAt())
                    .as("the least-recently-seen pre-existing session must have been evicted")
                    .isNotNull();

            // --- Part 2: a different user is never blocked by user A's lock ---
            Connection holder = dataSource.getConnection();
            try {
                try (PreparedStatement ps = holder.prepareStatement(
                        "SELECT GET_LOCK('session_cap:" + userA + "', 30)")) {
                    var rs = ps.executeQuery();
                    rs.next();
                    assertThat(rs.getLong(1)).as("test harness must hold user A's lock").isEqualTo(1L);
                }

                long start = System.nanoTime();
                CompanyContext.setCompanyId(companyId);
                try {
                    sessionService.openSession(userB, Duration.ofDays(1), false,
                            SessionService.DeviceInfo.of("device-b", "Test Device B", "127.0.0.1", "junit"));
                } finally {
                    CompanyContext.clear();
                }
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                assertThat(elapsedMs)
                        .as("a different user's login must not wait on user A's session-cap lock")
                        .isLessThan(2000);
            } finally {
                try (PreparedStatement ps = holder.prepareStatement(
                        "SELECT RELEASE_LOCK('session_cap:" + userA + "')")) {
                    ps.executeQuery();
                }
                holder.close();
            }
        } finally {
            CompanyContext.clear();
        }
    }

    private UUID resolveCompanyC1() {
        byte[] bytes = jdbcTemplate.queryForObject(
                "select company_id from companies where company_code = 'COMPANY-C1'", byte[].class);
        assertThat(bytes).as("standard dev fixture COMPANY-C1 must exist (see MEMORY/dev-login-credential.md)")
                .isNotNull();
        return bytesToUuid(bytes);
    }

    /** Minimal raw-JDBC user row, same technique as {@code PerfDataGeneratorRunner}. */
    private UUID createTestUser(UUID companyId, String slug) {
        UUID id = TimeOrderedUuid.generate();
        String email = slug + "-" + id + "@sessioncap-it.local";
        jdbcTemplate.update("""
                INSERT INTO users
                    (id, company_id, email, password_hash, first_name, last_name, status,
                     email_verified, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                TimeOrderedUuid.toBytes(id), TimeOrderedUuid.toBytes(companyId), email,
                "$2a$12$placeholderPlaceholderPlaceholderPlaceholderPlacehol",
                "SessionCap", "Test", "ACTIVE", false,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return id;
    }

    private List<UUID> seedActiveSessions(UUID userId, int count) {
        Instant base = Instant.now().minus(count, ChronoUnit.HOURS);
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            UserSession session = UserSession.builder()
                    .userId(userId)
                    .deviceId("seed-" + i)
                    .deviceName("Seed Device " + i)
                    .deviceType("DESKTOP")
                    .ipAddress("127.0.0.1")
                    .userAgent("junit-seed")
                    .lastSeenAt(base.plus(i, ChronoUnit.MINUTES))
                    .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                    .rememberMe(false)
                    .build();
            UserSession saved = sessionRepository.save(session);
            ids.add(saved.getId());
        }
        return ids;
    }

    private static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
