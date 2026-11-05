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

    private User targetHolds(Role... roles) {
        return targetHolds(UserStatus.ACTIVE, roles);
    }

    private User targetHolds(UserStatus status, Role... roles) {
        User user = User.builder().email("t@y.com").status(status)
                .roles(EnumSet.of(roles[0], roles)).build();
        when(users.findByIdForUpdate(targetId)).thenReturn(Optional.of(user));
        return user;
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
        when(incidents.existsByUserIdAndRuleAndStatusIn(
                targetId, IncidentRule.SESSION_MULTI_IP, Set.of(IncidentStatus.OPEN, IncidentStatus.BANNED)))
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

        Instant before = Instant.now();
        assertThat(service.raise(request(IncidentRule.SESSION_MULTI_IP))).isEmpty();
        Instant after = Instant.now();

        org.mockito.ArgumentCaptor<Instant> cutoff = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(incidents).existsByUserIdAndRuleAndStatusAndResolvedAtAfter(
                any(), any(), org.mockito.ArgumentMatchers.eq(IncidentStatus.DISMISSED), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(
                before.minus(java.time.Duration.ofHours(24)), after.minus(java.time.Duration.ofHours(24)));
        verify(incidents, never()).save(any());
    }

    @Test
    void raiseLocksUserRowBeforeDedupChecks() {
        targetHolds(Role.OPERATOR);

        service.raise(request(IncidentRule.SESSION_MULTI_IP));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(users, incidents);
        order.verify(users).findByIdForUpdate(targetId);
        order.verify(incidents).existsByUserIdAndRuleAndStatusIn(any(), any(), any());
        order.verify(incidents).save(any());
    }

    @Test
    void openAndBannedIncidentsSuppressButUnbannedDoesNot() {
        targetHolds(Role.OPERATOR);

        service.raise(request(IncidentRule.SESSION_MULTI_IP));

        verify(incidents).existsByUserIdAndRuleAndStatusIn(
                targetId, IncidentRule.SESSION_MULTI_IP, Set.of(IncidentStatus.OPEN, IncidentStatus.BANNED));
    }

    @Test
    void autoBanRuleForAlreadyDisabledUserReturnsEmpty() {
        targetHolds(UserStatus.DISABLED, Role.OPERATOR);

        assertThat(service.raise(request(IncidentRule.FOREIGN_TOKEN_USE))).isEmpty();

        verify(incidents, never()).save(any());
        verify(banService, never()).ban(any(), any());
    }

    @Test
    void nonAutoBanRuleStillRaisesForDisabledUser() {
        targetHolds(UserStatus.DISABLED, Role.OPERATOR);

        assertThat(service.raise(request(IncidentRule.SESSION_MULTI_IP))).isPresent();
    }

    @Test
    void raiseRunsInItsOwnTransaction() throws Exception {
        var raise = SecurityIncidentService.class.getMethod("raise", IncidentRequest.class);
        var tx = raise.getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
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

    @Test
    void callerWithNullCompanyCannotGetNullCompanyIncident() {
        actAs(UUID.randomUUID(), null, Role.COMPANY_ADMIN.name());
        SecurityIncident noCompany = stored(null, UUID.randomUUID(), IncidentStatus.OPEN);

        assertThatThrownBy(() -> service.get(noCompany.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.dismiss(noCompany.getId(), UUID.randomUUID(), "n"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void callerWithNullCompanyListsNothing() {
        actAs(UUID.randomUUID(), null, Role.COMPANY_ADMIN.name());

        assertThat(service.list(null, org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();

        verify(incidents, never()).findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<SecurityIncident>>any(),
                any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void unbanRejectedUnlessIncidentIsBanned() {
        SecurityIncident incident = stored(companyA, targetId, IncidentStatus.OPEN);

        assertThatThrownBy(() -> service.unban(incident.getId(), UUID.randomUUID(), "n"))
                .isInstanceOf(com.courier.shared.exception.BusinessRuleException.class);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        verify(banService, never()).unban(any());
    }

    @Test
    void unbanKeepsUserDisabledWhenAnotherIncidentIsStillBanned() {
        SecurityIncident incident = stored(companyA, targetId, IncidentStatus.BANNED);
        when(incidents.existsByUserIdAndStatusAndIdNot(targetId, IncidentStatus.BANNED, incident.getId()))
                .thenReturn(true);

        SecurityIncident result = service.unban(incident.getId(), UUID.randomUUID(), "ok");

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.UNBANNED);
        assertThat(result.getResolutionNote()).contains("another incident");
        verify(banService, never()).unban(any());
    }

    @Test
    void companyAdminCannotManuallyBanPlatformRoleTarget() {
        SecurityIncident incident = stored(companyA, targetId, IncidentStatus.OPEN);
        User platformUser = User.builder().email("p@y.com").status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.PLATFORM_ADMIN)).build();
        when(users.findById(targetId)).thenReturn(Optional.of(platformUser));

        assertThatThrownBy(() -> service.ban(incident.getId(), UUID.randomUUID(), "n"))
                .isInstanceOf(ForbiddenException.class);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        verify(banService, never()).ban(any(), any());
    }

    @Test
    void platformAdminMayManuallyBanSuperAdminTarget() {
        actAs(UUID.randomUUID(), null, Role.PLATFORM_ADMIN.name());
        SecurityIncident incident = stored(null, targetId, IncidentStatus.OPEN);
        User superUser = User.builder().email("s@y.com").status(UserStatus.ACTIVE)
                .roles(EnumSet.of(Role.SUPER_ADMIN)).build();
        when(users.findById(targetId)).thenReturn(Optional.of(superUser));

        SecurityIncident result = service.ban(incident.getId(), UUID.randomUUID(), "n");

        assertThat(result.getStatus()).isEqualTo(IncidentStatus.BANNED);
        verify(banService).ban(org.mockito.ArgumentMatchers.eq(targetId), anyString());
    }

    @Test
    void summaryCountsEachStatusWithinVisibility() {
        when(incidents.count(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<SecurityIncident>>any()))
                .thenReturn(3L, 1L, 0L, 2L);

        Map<IncidentStatus, Long> counts = service.countByStatus();

        assertThat(counts).containsOnlyKeys(IncidentStatus.values());
        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(6L);
    }

    @Test
    void summaryIsAllZeroForCallerWithNoCompany() {
        actAs(UUID.randomUUID(), null, Role.COMPANY_ADMIN.name());

        Map<IncidentStatus, Long> counts = service.countByStatus();

        assertThat(counts).containsOnlyKeys(IncidentStatus.values());
        assertThat(counts.values()).containsOnly(0L);
        verify(incidents, never()).count(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<SecurityIncident>>any());
    }
}
