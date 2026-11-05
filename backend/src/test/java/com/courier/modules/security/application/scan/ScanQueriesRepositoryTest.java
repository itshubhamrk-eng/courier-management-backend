package com.courier.modules.security.application.scan;

import com.courier.modules.auth.domain.LoginEventType;
import com.courier.modules.auth.domain.LoginFailureReason;
import com.courier.modules.auth.domain.LoginHistory;
import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.RefreshToken;
import com.courier.modules.auth.domain.RefreshTokenRepository;
import com.courier.modules.auth.domain.SessionRefreshEvent;
import com.courier.modules.security.domain.AutoAction;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.IncidentSeverity;
import com.courier.modules.security.domain.IncidentStatus;
import com.courier.modules.security.domain.SecurityIncident;
import com.courier.modules.security.domain.SecurityIncidentRepository;
import com.courier.shared.activity.domain.ActivityLog;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.ActivityStatus;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.audit.domain.AuditLog;
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
import java.util.List;
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
    @Autowired ActivityLogRepository activityLogs;
    @Autowired SecurityIncidentRepository incidents;
    @Autowired ScanCursorRepository cursors;
    @Autowired TestEntityManager em;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:10:00Z");
    final UUID company = TimeOrderedUuid.generate();

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

    /** refresh_tokens has an FK to users, so FK checks are relaxed for the test transaction. */
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
    void refreshTokenEventsKeepOneEntryPerRowEvenWhenIdentical() {
        relaxFks();
        UUID user = TimeOrderedUuid.generate();
        UUID session = TimeOrderedUuid.generate();
        token(user, session, "8.8.8.8", "ua", from.plusSeconds(5));
        token(user, session, "8.8.8.8", "ua", from.plusSeconds(65));       // same shape, second rotation
        token(user, session, "8.8.8.8", "ua", to);                         // end-exclusive
        em.clear();

        assertThat(refreshTokens.findRefreshTokenEvents(from, to))
                .filteredOn(e -> session.equals(e.sessionId()))
                .hasSize(2);
        assertThat(refreshTokens.findSessionRefreshEvents(from, to))      // the distinct one collapses them
                .filteredOn(e -> session.equals(e.sessionId()))
                .hasSize(1);
    }

    /**
     * Rows exactly as TokenIssuer writes them: a replayed, already-revoked refresh token plus
     * an audit row REFRESH_TOKEN_REUSE_DETECTED whose entity_id is that token's id (user and
     * company come from the token; the audit row's details JSON is not queried).
     */
    private void replay(UUID user, String revokedReason, String successorJti, Instant at) {
        RefreshToken t = token(user, TimeOrderedUuid.generate(), "8.8.8.8", "ua", at);
        em.getEntityManager().createNativeQuery(
                        "update refresh_tokens set revoked_at = ?1, revoked_reason = ?2, replaced_by_jti = ?3 where id = ?4")
                .setParameter(1, java.sql.Timestamp.from(at.minusSeconds(60)))
                .setParameter(2, revokedReason)
                .setParameter(3, successorJti)
                .setParameter(4, t.getId()).executeUpdate();
        em.persist(AuditLog.builder().action(AuditAction.REFRESH_TOKEN_REUSE_DETECTED)
                .entityType("RefreshToken").entityId(t.getId()).success(false).occurredAt(at).build());
        em.flush();
    }

    @Test
    void replaysOfTokensWithoutSuccessorCountAndThresholdIsStrict() {
        relaxFks();
        UUID user = TimeOrderedUuid.generate();
        UUID rotatedUser = TimeOrderedUuid.generate();
        for (int i = 0; i < 3; i++) {
            replay(user, "LOGOUT", null, from.plusSeconds(i + 1));
            replay(rotatedUser, "ROTATED", "successor-jti", from.plusSeconds(i + 1));
        }
        replay(user, "LOGOUT", null, to);                                  // end-exclusive
        replay(user, "SESSION_EVICTED", null, from.minusSeconds(1));       // before window
        em.clear();

        assertThat(refreshTokens.findUsersWithRevokedTokenReplaysAbove(from, to, 3))
                .noneMatch(r -> user.equals(r.userId()));
        assertThat(refreshTokens.findUsersWithRevokedTokenReplaysAbove(from, to, 2))
                .filteredOn(r -> user.equals(r.userId())).singleElement()
                .satisfies(r -> {
                    assertThat(r.count()).isEqualTo(3L);
                    assertThat(r.companyId()).isEqualTo(company);
                });
        // rotated-token replays belong to REFRESH_TOKEN_REUSE: never counted here
        assertThat(refreshTokens.findUsersWithRevokedTokenReplaysAbove(from, to, 0))
                .noneMatch(r -> rotatedUser.equals(r.userId()));
    }

    // ------------------------------------------------------------ CREDENTIAL_ATTACK

    /** Shaped like LoginAttemptService.recordFailure / recordSessionEnd rows. */
    private void loginRow(String email, String ip, LoginEventType type, boolean success,
                          LoginFailureReason reason, Instant at) {
        relaxFks();
        LoginHistory h = LoginHistory.builder().attemptedEmail(email).ipAddress(ip)
                .eventType(type).success(success).failureReason(reason).occurredAt(at).build();
        h.setCompanyId(company);
        em.persist(h);
    }

    private void failure(String email, String ip, LoginFailureReason reason, Instant at) {
        loginRow(email, ip, LoginEventType.LOGIN_FAILED, false, reason, at);
    }

    @Test
    void loginFailuresGroupByIpAndCompanyCountingOnlyLoginFailedRowsInWindow() {
        String ip = "203.0.113.7";
        failure("a@x.in", ip, LoginFailureReason.UNKNOWN_USER, from.plusSeconds(1));
        failure("b@x.in", ip, LoginFailureReason.BAD_PASSWORD, from.plusSeconds(2));
        failure("b@x.in", ip, LoginFailureReason.BAD_PASSWORD, from.plusSeconds(3));
        failure("c@x.in", ip, LoginFailureReason.GEO_RESTRICTED, from.plusSeconds(4));
        // SESSION_EXPIRED rows are success=false too, but they are not login failures
        loginRow("d@x.in", ip, LoginEventType.SESSION_EXPIRED, false, null, from.plusSeconds(5));
        loginRow("e@x.in", ip, LoginEventType.LOGIN_SUCCESS, true, null, from.plusSeconds(6));
        failure("f@x.in", ip, LoginFailureReason.UNKNOWN_USER, to);                  // end-exclusive
        failure("g@x.in", ip, LoginFailureReason.UNKNOWN_USER, from.minusSeconds(1));
        em.flush();
        em.clear();

        assertThat(loginHistory.findLoginFailuresByIp(from, to))
                .filteredOn(r -> company.equals(r.companyId()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.ipAddress()).isEqualTo(ip);
                    assertThat(r.distinctEmails()).isEqualTo(3L);
                    assertThat(r.failures()).isEqualTo(4L);
                });
    }

    @Test
    void accountFailuresAreCountedPerCompanyEmailWithThreshold() {
        for (int i = 0; i < 4; i++) {
            failure("ops@x.in", "198.51.100." + (i % 2), LoginFailureReason.BAD_PASSWORD, from.plusSeconds(i + 1));
        }
        failure("ops@x.in", "198.51.100.9", LoginFailureReason.ACCOUNT_LOCKED, from.plusSeconds(10));
        loginRow("ops@x.in", "198.51.100.9", LoginEventType.SESSION_EXPIRED, false, null, from.plusSeconds(11));
        em.flush();
        em.clear();

        assertThat(loginHistory.findAccountsWithLoginFailuresAtLeast(from, to, 6))
                .noneMatch(r -> company.equals(r.companyId()));
        assertThat(loginHistory.findAccountsWithLoginFailuresAtLeast(from, to, 5))
                .filteredOn(r -> company.equals(r.companyId())).singleElement()
                .satisfies(r -> {
                    assertThat(r.attemptedEmail()).isEqualTo("ops@x.in");
                    assertThat(r.failures()).isEqualTo(5L);
                    assertThat(r.distinctIps()).isEqualTo(3L);
                });
    }

    private SecurityIncident incident(IncidentRule rule, UUID companyId, String evidence, Instant detectedAt) {
        relaxFks();
        return incidents.saveAndFlush(SecurityIncident.builder().companyId(companyId).rule(rule)
                .severity(IncidentSeverity.MEDIUM).status(IncidentStatus.OPEN).evidence(evidence)
                .autoAction(AutoAction.NONE).detectedAt(detectedAt).build());
    }

    @Test
    void recentIncidentIsFoundByEvidenceIpDespiteMysqlJsonReformatting() {
        Instant since = to.minusSeconds(1200);
        incident(IncidentRule.CREDENTIAL_ATTACK, company,
                "{\"ip\":\"203.0.113.7\",\"distinctAccounts\":6}", to.minusSeconds(60));
        em.clear();

        assertThat(incidents.existsRecentForEvidenceIp(IncidentRule.CREDENTIAL_ATTACK, company, "203.0.113.7", since))
                .isTrue();
        assertThat(incidents.existsRecentForEvidenceIp(IncidentRule.CREDENTIAL_ATTACK, company, "203.0.113.8", since))
                .isFalse();
        assertThat(incidents.existsRecentForEvidenceIp(
                IncidentRule.CREDENTIAL_ATTACK, TimeOrderedUuid.generate(), "203.0.113.7", since)).isFalse();
        assertThat(incidents.existsRecentForEvidenceIp(IncidentRule.SESSION_MULTI_IP, company, "203.0.113.7", since))
                .isFalse();
        assertThat(incidents.existsRecentForEvidenceIp(
                IncidentRule.CREDENTIAL_ATTACK, company, "203.0.113.7", to)).isFalse();     // older than lookback
    }

    // ------------------------------------------------- BULK_ACTIVITY / ACCESS_DENIED_BURST

    /** Shaped like ActivityLoggingFilter rows: module from the URL, action from verb/keyword. */
    private void activity(UUID user, String module, String action, String method, String endpoint,
                          ActivityStatus status, String errorMessage, Instant at) {
        relaxFks();
        em.persist(ActivityLog.builder().companyId(company).userId(user).username("u@x.in")
                .module(module).action(action).requestMethod(method).apiEndpoint(endpoint)
                .status(status).errorMessage(errorMessage).ipAddress("10.0.0.1").occurredAt(at).build());
    }

    @Test
    void successCountsByActionIgnoreFailuresAndOtherActions() {
        UUID user = TimeOrderedUuid.generate();
        Instant t = from.plusSeconds(5);
        activity(user, "Shipments", "CANCEL", "POST", "/api/v1/shipments/{id}/cancel", ActivityStatus.SUCCESS, null, t);
        activity(user, "Shipments", "CANCEL", "POST", "/api/v1/shipments/{id}/cancel", ActivityStatus.SUCCESS, null, t);
        activity(user, "Pickups", "CANCEL", "PATCH", "/api/v1/pickups/{id}/cancel", ActivityStatus.SUCCESS, null, t);
        activity(user, "Shipments", "CANCEL", "POST", "/api/v1/shipments/{id}/cancel",
                ActivityStatus.FAILURE, "Shipment cannot be cancelled", t);
        activity(user, "Shipments", "CREATE", "POST", "/api/v1/shipments", ActivityStatus.SUCCESS, null, t);
        activity(user, "Shipments", "CANCEL", "POST", "/api/v1/shipments/{id}/cancel", ActivityStatus.SUCCESS, null, to);
        em.flush();
        em.clear();

        assertThat(activityLogs.findSuccessCountsByAction(from, to, List.of("CANCEL", "EXPORT")))
                .filteredOn(r -> user.equals(r.userId()))
                .extracting(r -> r.module() + " " + r.action() + "=" + r.count())
                .containsExactlyInAnyOrder("Shipments CANCEL=2", "Pickups CANCEL=1");
    }

    @Test
    void rateEditCountsExcludeCalculateAndOtherModules() {
        UUID user = TimeOrderedUuid.generate();
        Instant t = from.plusSeconds(5);
        activity(user, "Rates", "UPDATE", "PUT", "/api/v1/rates/{id}", ActivityStatus.SUCCESS, null, t);
        activity(user, "Customer Rates", "DELETE", "DELETE", "/api/v1/customer-rates/zone/{zoneId}",
                ActivityStatus.SUCCESS, null, t);
        activity(user, "Rates", "CREATE", "POST", "/api/v1/rates/calculate", ActivityStatus.SUCCESS, null, t);
        activity(user, "Pricing Zones", "CREATE", "POST", "/api/v1/pricing-zones/{id}/locations",
                ActivityStatus.SUCCESS, null, t);
        activity(user, "Pricing Zones", "DELETE", "DELETE", "/api/v1/pricing-zones/{id}/locations/{locationId}",
                ActivityStatus.SUCCESS, null, t);
        activity(user, "Pricing Zones", "UPDATE", "PUT", "/api/v1/pricing-zones/{id}", ActivityStatus.SUCCESS, null, t);
        activity(user, "Shipments", "UPDATE", "PUT", "/api/v1/shipments/{id}", ActivityStatus.SUCCESS, null, t);
        em.flush();
        em.clear();

        assertThat(activityLogs.findSuccessCountsByModuleAndAction(from, to,
                List.of("Rates", "Customer Rates", "Pdp Rates", "Pricing Zones"),
                List.of("CREATE", "UPDATE", "DELETE", "ACTIVATE", "DEACTIVATE"),
                List.of("/api/v1/rates/calculate", "/api/v1/pricing-zones/{id}/locations",
                        "/api/v1/pricing-zones/{id}/locations/{locationId}")))
                .filteredOn(r -> user.equals(r.userId()))
                .extracting(r -> r.module() + " " + r.action() + "=" + r.count())
                .containsExactlyInAnyOrder("Rates UPDATE=1", "Customer Rates DELETE=1", "Pricing Zones UPDATE=1");
    }

    @Test
    void deniedCountsMatchOnlyTheAccessDeniedMessageSo404sNeverCount() {
        UUID user = TimeOrderedUuid.generate();
        String denied = ErrorCode.ACCESS_DENIED.getDefaultMessage();
        Instant t = from.plusSeconds(5);
        activity(user, "Rates", "UPDATE", "PUT", "/api/v1/rates/{id}", ActivityStatus.FAILURE, denied, t);
        activity(user, "Rates", "UPDATE", "PUT", "/api/v1/rates/{id}", ActivityStatus.FAILURE, denied, t);
        activity(user, "Users", "VIEW", "GET", "/api/v1/users/{id}", ActivityStatus.FAILURE, denied, t);
        activity(user, "Users", "VIEW", "GET", "/api/v1/users/{id}", ActivityStatus.FAILURE, "Resource not found", t);
        activity(user, "Users", "VIEW", "GET", "/api/v1/users/{id}", ActivityStatus.FAILURE, "Endpoint not found", t);
        activity(user, "Rates", "UPDATE", "PUT", "/api/v1/rates/{id}", ActivityStatus.FAILURE, denied, to);
        em.flush();
        em.clear();

        assertThat(activityLogs.findFailureCountsByErrorMessage(from, to, denied))
                .filteredOn(r -> user.equals(r.userId()))
                .extracting(r -> r.requestMethod() + " " + r.apiEndpoint() + "=" + r.count())
                .containsExactlyInAnyOrder("PUT /api/v1/rates/{id}=2", "GET /api/v1/users/{id}=1");
    }

    @Test
    void cursorRoundTrips() {
        cursors.deleteAll();
        cursors.saveAndFlush(new ScanCursor(ScanCursor.SINGLETON_ID, from));

        assertThat(cursors.findById(ScanCursor.SINGLETON_ID)).get()
                .extracting(ScanCursor::getLastScannedAt).isEqualTo(from);
    }
}
