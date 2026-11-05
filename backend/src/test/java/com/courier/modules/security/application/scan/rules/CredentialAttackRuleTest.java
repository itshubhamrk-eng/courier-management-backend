package com.courier.modules.security.application.scan.rules;

import com.courier.modules.auth.domain.LoginFailureAccountCount;
import com.courier.modules.auth.domain.LoginFailureIpCount;
import com.courier.modules.auth.domain.LoginHistoryRepository;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.security.application.IncidentRequest;
import com.courier.modules.security.application.SecurityProperties;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.SecurityIncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialAttackRuleTest {

    @Mock LoginHistoryRepository loginHistoryRepository;
    @Mock UserRepository userRepository;
    @Mock SecurityIncidentRepository incidentRepository;

    final Instant from = Instant.parse("2026-11-05T10:00:00Z");
    final Instant to = Instant.parse("2026-11-05T10:10:00Z");
    final UUID companyA = UUID.randomUUID();
    final UUID companyB = UUID.randomUUID();
    final SecurityProperties props = new SecurityProperties();

    private CredentialAttackRule rule() {
        return new CredentialAttackRule(loginHistoryRepository, userRepository, incidentRepository, props);
    }

    private void noAccountRows() {
        when(loginHistoryRepository.findAccountsWithLoginFailuresAtLeast(eq(from), eq(to), anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void codeIsCredentialAttackAndNeverAutoBans() {
        assertThat(rule().code()).isEqualTo(IncidentRule.CREDENTIAL_ATTACK);
        assertThat(IncidentRule.CREDENTIAL_ATTACK.autoBan()).isFalse();
    }

    @Test
    void defaults() {
        assertThat(props.getCredentialAttack().getDistinctAccounts()).isEqualTo(5);
        assertThat(props.getCredentialAttack().getFailuresPerAccount()).isEqualTo(10);
    }

    @Test
    void noFailuresRaiseNothing() {
        when(loginHistoryRepository.findLoginFailuresByIp(from, to)).thenReturn(List.of());
        noAccountRows();

        assertThat(rule().scan(from, to)).isEmpty();
    }

    @Test
    void ipBelowDistinctAccountThresholdRaisesNothing() {
        when(loginHistoryRepository.findLoginFailuresByIp(from, to))
                .thenReturn(List.of(new LoginFailureIpCount("203.0.113.7", companyA, 4, 40)));
        noAccountRows();

        assertThat(rule().scan(from, to)).isEmpty();
        verify(incidentRepository, never()).existsRecentForEvidenceIp(any(), any(), any(), any());
    }

    @Test
    void sprayFromOneIpRaisesCompanyScopedRequestWithNullUser() {
        when(loginHistoryRepository.findLoginFailuresByIp(from, to))
                .thenReturn(List.of(new LoginFailureIpCount("203.0.113.7", companyA, 5, 9)));
        noAccountRows();

        List<IncidentRequest> out = rule().scan(from, to);

        assertThat(out).singleElement().satisfies(r -> {
            assertThat(r.rule()).isEqualTo(IncidentRule.CREDENTIAL_ATTACK);
            assertThat(r.userId()).isNull();
            assertThat(r.companyId()).isEqualTo(companyA);
            assertThat(r.evidence())
                    .containsEntry("ip", "203.0.113.7")
                    .containsEntry("distinctAccounts", 5L)
                    .containsEntry("distinctAccountsInCompany", 5L)
                    .containsEntry("failuresInCompany", 9L)
                    .containsEntry("threshold", 5)
                    .containsEntry("windowFrom", from.toString())
                    .containsEntry("windowTo", to.toString());
        });
    }

    @Test
    void sprayAcrossCompaniesSumsAccountsAndRaisesOnePerCompany() {
        when(loginHistoryRepository.findLoginFailuresByIp(from, to)).thenReturn(List.of(
                new LoginFailureIpCount("203.0.113.7", companyA, 3, 3),
                new LoginFailureIpCount("203.0.113.7", companyB, 2, 2),
                new LoginFailureIpCount("198.51.100.1", companyA, 1, 8)));
        noAccountRows();

        List<IncidentRequest> out = rule().scan(from, to);

        assertThat(out).extracting(IncidentRequest::companyId).containsExactlyInAnyOrder(companyA, companyB);
        assertThat(out).allSatisfy(r -> assertThat(r.evidence())
                .containsEntry("ip", "203.0.113.7").containsEntry("distinctAccounts", 5L));
    }

    @Test
    void secondOverlappingRunIsSuppressedByExistingIncidentForTheSameIp() {
        when(loginHistoryRepository.findLoginFailuresByIp(any(), any()))
                .thenReturn(List.of(new LoginFailureIpCount("203.0.113.7", companyA, 6, 6)));
        when(loginHistoryRepository.findAccountsWithLoginFailuresAtLeast(any(), any(), anyLong()))
                .thenReturn(List.of());
        // First run: nothing recorded yet. Second run: the first run's incident now exists.
        when(incidentRepository.existsRecentForEvidenceIp(
                eq(IncidentRule.CREDENTIAL_ATTACK), eq(companyA), eq("203.0.113.7"), any()))
                .thenReturn(false, true);

        assertThat(rule().scan(from, to)).hasSize(1);
        Instant from2 = from.plusSeconds(120);
        Instant to2 = to.plusSeconds(120);
        assertThat(rule().scan(from2, to2)).isEmpty();

        // Lookback is twice the scan window, measured from the window end.
        verify(incidentRepository).existsRecentForEvidenceIp(
                IncidentRule.CREDENTIAL_ATTACK, companyA, "203.0.113.7", to2.minus(props.getScanWindow().multipliedBy(2)));
    }

    @Test
    void accountAboveFailureThresholdRaisesForResolvedUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().email("ops@acme.in").build();
        user.setId(userId);
        when(loginHistoryRepository.findLoginFailuresByIp(from, to)).thenReturn(List.of());
        when(loginHistoryRepository.findAccountsWithLoginFailuresAtLeast(from, to, 10))
                .thenReturn(List.of(new LoginFailureAccountCount(companyA, "ops@acme.in", 12, 3)));
        when(userRepository.findByEmailWithinCompany("ops@acme.in", companyA)).thenReturn(Optional.of(user));

        List<IncidentRequest> out = rule().scan(from, to);

        assertThat(out).singleElement().satisfies(r -> {
            assertThat(r.rule()).isEqualTo(IncidentRule.CREDENTIAL_ATTACK);
            assertThat(r.userId()).isEqualTo(userId);
            assertThat(r.companyId()).isEqualTo(companyA);
            assertThat(r.evidence())
                    .containsEntry("attemptedEmail", "ops@acme.in")
                    .containsEntry("failures", 12L)
                    .containsEntry("distinctIps", 3L)
                    .containsEntry("threshold", 10)
                    .doesNotContainKey("ip");
        });
    }

    @Test
    void accountThatMatchesNoUserIsSkipped() {
        when(loginHistoryRepository.findLoginFailuresByIp(from, to)).thenReturn(List.of());
        when(loginHistoryRepository.findAccountsWithLoginFailuresAtLeast(from, to, 10))
                .thenReturn(List.of(new LoginFailureAccountCount(companyA, "nobody@acme.in", 15, 1)));
        when(userRepository.findByEmailWithinCompany("nobody@acme.in", companyA)).thenReturn(Optional.empty());

        assertThat(rule().scan(from, to)).isEmpty();
    }
}
