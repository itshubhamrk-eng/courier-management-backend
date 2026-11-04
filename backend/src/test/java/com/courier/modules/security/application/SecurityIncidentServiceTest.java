package com.courier.modules.security.application;

import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.modules.security.domain.AutoAction;
import com.courier.modules.security.domain.IncidentRule;
import com.courier.modules.security.domain.IncidentSeverity;
import com.courier.modules.security.domain.IncidentStatus;
import com.courier.modules.security.domain.SecurityIncident;
import com.courier.modules.security.domain.SecurityIncidentRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityIncidentServiceTest {

    private final SecurityIncidentRepository incidents = mock(SecurityIncidentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserBanService banService = mock(UserBanService.class);
    private final AuditService audit = mock(AuditService.class);

    private SecurityIncidentService service;
    private final UUID companyA = UUID.randomUUID();
    private final UUID companyB = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SecurityIncidentService(incidents, users, banService, audit, new ObjectMapper());
        when(incidents.save(any(SecurityIncident.class))).thenAnswer(inv -> inv.getArgument(0));
        actAs(UUID.randomUUID(), companyA, Role.COMPANY_ADMIN.name());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(UUID userId, UUID companyId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, companyId, "x@y.com", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private void targetHolds(Role... roles) {
        User user = User.builder().email("t@y.com").status(UserStatus.ACTIVE)
                .roles(EnumSet.of(roles[0], roles)).build();
        when(users.findById(targetId)).thenReturn(Optional.of(user));
    }

    private IncidentRequest request(IncidentRule rule) {
        return new IncidentRequest(companyA, targetId, rule, Map.of("ip", "1.2.3.4"));
    }

    private SecurityIncident stored(UUID company, UUID user, IncidentStatus status) {
        SecurityIncident incident = SecurityIncident.builder()
                .companyId(company).userId(user).rule(IncidentRule.SESSION_MULTI_IP)
                .severity(IncidentSeverity.MEDIUM).status(status).autoAction(AutoAction.NONE)
                .detectedAt(Instant.now()).build();
        incident.setId(UUID.randomUUID());
        when(incidents.findById(incident.getId())).thenReturn(Optional.of(incident));
        return incident;
    }

    @Test
    void autoBanRuleRaisesHighIncidentAndBansUser() {
        targetHolds(Role.OPERATOR);

        SecurityIncident result = service.raise(request(IncidentRule.REFRESH_TOKEN_REUSE)).orElseThrow();

        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.HIGH);
        assertThat(result.getAutoAction()).isEqualTo(AutoAction.USER_DISABLED);
        assertThat(result.getStatus()).isEqualTo(IncidentStatus.BANNED);
        assertThat(result.getEvidence()).contains("\"ip\":\"1.2.3.4\"");
        verify(banService).ban(org.mockito.ArgumentMatchers.eq(targetId), anyString());
    }

    @Test
    void nonAutoBanRuleLeavesUserUntouched() {
        SecurityIncident result = service.raise(request(IncidentRule.SESSION_MULTI_IP)).orElseThrow();

        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.MEDIUM);
        assertThat(result.getAutoAction()).isEqualTo(AutoAction.NONE);
        assertThat(result.getStatus()).isEqualTo(IncidentStatus.OPEN);
        verify(banService, never()).ban(any(), any());
    }

    @Test
    void secondRaiseForSameOpenUserAndRuleReturnsEmptyAndSavesNothing() {
        when(incidents.existsByUserIdAndRuleAndStatus(targetId, IncidentRule.SESSION_MULTI_IP, IncidentStatus.OPEN))
                .thenReturn(true);

        assertThat(service.raise(request(IncidentRule.SESSION_MULTI_IP))).isEmpty();

        verify(incidents, never()).save(any());
    }

    @Test
    void raiseWithinDayOfDismissalReturnsEmpty() {
        when(incidents.existsByUserIdAndRuleAndStatusAndResolvedAtAfter(
                org.mockito.ArgumentMatchers.eq(targetId),
                org.mockito.ArgumentMatchers.eq(IncidentRule.SESSION_MULTI_IP),
                org.mockito.ArgumentMatchers.eq(IncidentStatus.DISMISSED), any(Instant.class)))
                .thenReturn(true);

        assertThat(service.raise(request(IncidentRule.SESSION_MULTI_IP))).isEmpty();

        verify(incidents, never()).save(any());
    }

    @Test
    void superAdminTargetGetsIncidentOnlyWithoutBan() {
        targetHolds(Role.SUPER_ADMIN);

        SecurityIncident result = service.raise(request(IncidentRule.FOREIGN_TOKEN_USE)).orElseThrow();

        assertThat(result.getAutoAction()).isEqualTo(AutoAction.NONE);
        assertThat(result.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.HIGH);
        verify(banService, never()).ban(any(), any());
    }

    @Test
    void platformAdminTargetIsNotBannedEither() {
        targetHolds(Role.PLATFORM_ADMIN);

        SecurityIncident result = service.raise(request(IncidentRule.FOREIGN_TOKEN_USE)).orElseThrow();

        assertThat(result.getAutoAction()).isEqualTo(AutoAction.NONE);
        verify(banService, never()).ban(any(), any());
    }

    @Test
    void adminCannotBanThemselvesViaIncident() {
        UUID self = UUID.randomUUID();
        actAs(self, companyA, Role.COMPANY_ADMIN.name());
        SecurityIncident incident = stored(companyA, self, IncidentStatus.OPEN);

        assertThatThrownBy(() -> service.ban(incident.getId(), self, "n"))
                .isInstanceOf(ForbiddenException.class);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        verify(banService, never()).ban(any(), any());
    }

    @Test
    void banRecordsResolutionAndBansUser() {
        SecurityIncident incident = stored(companyA, targetId, IncidentStatus.OPEN);
        UUID actor = UUID.randomUUID();

        SecurityIncident result = service.ban(incident.getId(), actor, "confirmed");

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.BANNED);
        assertThat(result.getResolvedBy()).isEqualTo(actor);
        assertThat(result.getResolvedAt()).isNotNull();
        assertThat(result.getResolutionNote()).isEqualTo("confirmed");
        verify(banService).ban(org.mockito.ArgumentMatchers.eq(targetId), anyString());
    }

    @Test
    void dismissMarksDismissedWithoutBan() {
        SecurityIncident incident = stored(companyA, targetId, IncidentStatus.OPEN);

        SecurityIncident result = service.dismiss(incident.getId(), UUID.randomUUID(), "false alarm");

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.DISMISSED);
        assertThat(result.getResolvedAt()).isNotNull();
        verify(banService, never()).ban(any(), any());
    }

    @Test
    void unbanMarksUnbannedAndLiftsBan() {
        SecurityIncident incident = stored(companyA, targetId, IncidentStatus.BANNED);

        SecurityIncident result = service.unban(incident.getId(), UUID.randomUUID(), "ok");

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.UNBANNED);
        verify(banService).unban(targetId);
    }

    @Test
    void companyAdminCannotGetAnotherCompanysIncident() {
        SecurityIncident other = stored(companyB, UUID.randomUUID(), IncidentStatus.OPEN);

        assertThatThrownBy(() -> service.get(other.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void companyAdminCannotActOnAnotherCompanysIncident() {
        SecurityIncident other = stored(companyB, UUID.randomUUID(), IncidentStatus.OPEN);

        assertThatThrownBy(() -> service.dismiss(other.getId(), UUID.randomUUID(), "n"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(other.getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    void platformAdminSeesAnyIncidentIncludingNullCompany() {
        actAs(UUID.randomUUID(), null, Role.PLATFORM_ADMIN.name());
        SecurityIncident noCompany = stored(null, UUID.randomUUID(), IncidentStatus.OPEN);
        SecurityIncident otherCompany = stored(companyB, UUID.randomUUID(), IncidentStatus.OPEN);

        assertThat(service.get(noCompany.getId())).isSameAs(noCompany);
        assertThat(service.get(otherCompany.getId())).isSameAs(otherCompany);
    }

    @Test
    void ownCompanyIncidentIsVisibleToCompanyAdmin() {
        SecurityIncident own = stored(companyA, UUID.randomUUID(), IncidentStatus.OPEN);

        assertThat(service.get(own.getId())).isSameAs(own);
    }
}
