package com.courier.shared.activity.application;

import com.courier.shared.activity.domain.ActivityLog;
import com.courier.shared.activity.domain.ActivityLogCriteria;
import com.courier.shared.activity.domain.ActivityLogRepository;
import com.courier.shared.activity.domain.ActivityStatus;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Company isolation is this module's whole point (requirement 8) — a user from Company A
 * must never see Company B's activity. These tests exercise exactly that, plus the
 * platform-tier exception every other module's search criteria already grants (AI_CONTEXT.md
 * decision 27).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityLogServiceTest {

    private static final UUID CALLER_COMPANY = UUID.randomUUID();
    private static final UUID OTHER_COMPANY = UUID.randomUUID();

    @Mock private ActivityLogRepository repository;
    @Mock private ActivityLogWriter writer;

    private ActivityLogService service;

    @BeforeEach
    void setUp() {
        service = new ActivityLogService(repository, writer, new SensitiveDataMasker(new ObjectMapper()),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a company caller's own company id always wins, whatever was requested")
    void companyCallerIsPinnedToOwnCompany() {
        signedIn(CALLER_COMPANY, Roles.COMPANY_ADMIN);
        ActivityLogCriteria requested = criteriaFor(OTHER_COMPANY);

        ActivityLogCriteria scoped = service.scopeToCaller(requested);

        assertThat(scoped.companyId()).isEqualTo(CALLER_COMPANY);
    }

    @Test
    @DisplayName("a super admin's requested company id is honoured, not overridden")
    void superAdminMayScopeAnyCompany() {
        signedIn(null, Roles.SUPER_ADMIN);
        ActivityLogCriteria requested = criteriaFor(OTHER_COMPANY);

        ActivityLogCriteria scoped = service.scopeToCaller(requested);

        assertThat(scoped.companyId()).isEqualTo(OTHER_COMPANY);
    }

    @Test
    @DisplayName("reading another company's entry 404s rather than 403s, so its existence isn't confirmed")
    void crossCompanyReadIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entryFor(OTHER_COMPANY)));
        signedIn(CALLER_COMPANY, Roles.COMPANY_ADMIN);

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a super admin may read any company's entry")
    void superAdminReadsAcrossCompanies() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entryFor(OTHER_COMPANY)));
        signedIn(null, Roles.SUPER_ADMIN);

        assertThat(service.getById(id).getCompanyId()).isEqualTo(OTHER_COMPANY);
    }

    @Test
    @DisplayName("the same company's own entry reads back fine")
    void sameCompanyReadSucceeds() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entryFor(CALLER_COMPANY)));
        signedIn(CALLER_COMPANY, Roles.COMPANY_ADMIN);

        assertThat(service.getById(id).getCompanyId()).isEqualTo(CALLER_COMPANY);
    }

    private ActivityLogCriteria criteriaFor(UUID companyId) {
        return new ActivityLogCriteria(companyId, null, null, null, null, null, null, null, null, null, null);
    }

    private ActivityLog entryFor(UUID companyId) {
        return ActivityLog.builder().companyId(companyId).action("VIEW")
                .status(ActivityStatus.SUCCESS).occurredAt(Instant.now()).build();
    }

    private void signedIn(UUID companyId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), companyId, "caller@test.example", Set.of(role), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
