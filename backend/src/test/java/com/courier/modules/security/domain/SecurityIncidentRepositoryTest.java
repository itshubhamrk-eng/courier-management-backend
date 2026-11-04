package com.courier.modules.security.domain;

import com.courier.shared.config.JpaAuditingConfig;
import com.courier.shared.domain.TimeOrderedUuid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migrations against a scratch MySQL schema and then exercises the
 * repository, so the V139 DDL, the entity mapping ({@code ddl-auto=validate}) and the
 * dedup queries are all checked together. Skipped unless {@code SECURITY_IT_DB_URL} is
 * set (e.g. {@code jdbc:mysql://localhost:3306/security_incident_test?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}),
 * with {@code SECURITY_IT_DB_USER} / {@code SECURITY_IT_DB_PASSWORD}. Never point it at a
 * database that holds data you care about.
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
class SecurityIncidentRepositoryTest {

    @Autowired
    private SecurityIncidentRepository repository;

    @Test
    void dedupQueriesFindOpenAndRecentlyDismissed() {
        UUID user = TimeOrderedUuid.generate();
        Instant now = Instant.now();

        repository.saveAndFlush(incident(user, IncidentRule.REFRESH_TOKEN_REUSE,
                IncidentStatus.OPEN, null));

        assertThat(repository.existsByUserIdAndRuleAndStatus(
                user, IncidentRule.REFRESH_TOKEN_REUSE, IncidentStatus.OPEN)).isTrue();
        assertThat(repository.existsByUserIdAndRuleAndStatus(
                user, IncidentRule.BULK_ACTIVITY, IncidentStatus.OPEN)).isFalse();

        repository.saveAndFlush(incident(user, IncidentRule.SESSION_MULTI_IP,
                IncidentStatus.DISMISSED, now.minus(Duration.ofHours(1))));

        assertThat(repository.existsByUserIdAndRuleAndStatusAndResolvedAtAfter(
                user, IncidentRule.SESSION_MULTI_IP, IncidentStatus.DISMISSED,
                now.minus(Duration.ofHours(24)))).isTrue();
        assertThat(repository.existsByUserIdAndRuleAndStatusAndResolvedAtAfter(
                user, IncidentRule.SESSION_MULTI_IP, IncidentStatus.DISMISSED,
                now.minus(Duration.ofMinutes(30)))).isFalse();
    }

    @Test
    void onlyTheFirstThreeRulesAutoBan() {
        assertThat(IncidentRule.values()).filteredOn(IncidentRule::autoBan)
                .containsExactly(IncidentRule.REFRESH_TOKEN_REUSE,
                        IncidentRule.FOREIGN_LOGIN_CORRECT_PASSWORD,
                        IncidentRule.FOREIGN_TOKEN_USE);
    }

    private static SecurityIncident incident(UUID user, IncidentRule rule,
                                             IncidentStatus status, Instant resolvedAt) {
        return SecurityIncident.builder()
                .userId(user)
                .rule(rule)
                .severity(IncidentSeverity.HIGH)
                .status(status)
                .evidence("{\"ip\":\"203.0.113.9\"}")
                .autoAction(AutoAction.NONE)
                .detectedAt(Instant.now())
                .resolvedAt(resolvedAt)
                .build();
    }
}
