package com.courier.modules.platformintegration.application;

import com.courier.modules.company.application.CompanyService;
import com.courier.modules.company.application.command.CreateCompanyCommand;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.company.domain.CompanyStatus;
import com.courier.modules.platformintegration.infrastructure.PlatformIntegrationProperties;
import com.courier.modules.subscription.application.SubscriptionPlanService;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformTenantServiceImplTest {

    private static final UUID EXTERNAL = UUID.randomUUID();

    @Mock CompanyService companyService;
    @Mock CompanyRepository companyRepository;
    @Mock SubscriptionPlanService planService;

    PlatformTenantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlatformTenantServiceImpl(companyService, companyRepository, planService,
                new PlatformIntegrationProperties(true, "iss", "jwks", "aud", "client", "STARTER", null));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static PlatformTenantService.ProvisionCommand command(String email, String mobile) {
        return new PlatformTenantService.ProvisionCommand("ACME", "Acme Industries", email, "Asha", "Rao", mobile, null);
    }

    @Test
    void createsCompanyOnceAndLinksExternalId() {
        UUID planId = UUID.randomUUID();
        SubscriptionPlan plan = SubscriptionPlan.builder().build();
        plan.setId(planId);
        when(planService.getByCode("STARTER")).thenReturn(plan);
        when(companyRepository.findByExternalCompanyId(EXTERNAL)).thenReturn(Optional.empty());
        Company company = Company.builder().companyId(UUID.randomUUID()).build();
        AtomicBoolean ranAsSuperAdmin = new AtomicBoolean();
        when(companyService.create(any())).thenAnswer(i -> {
            ranAsSuperAdmin.set(SecurityUtils.getCurrentUser().map(u -> u.isSuperAdmin()).orElse(false));
            return new CompanyService.CreatedCompany(company, UUID.randomUUID(), "a@b.test", "x", true, 1, 1);
        });

        PlatformTenantService.Result result = service.provision(EXTERNAL, command("admin@acme.test", "9999999999"));

        assertThat(result.created()).isTrue();
        assertThat(result.companyId()).isEqualTo(company.getCompanyId());
        assertThat(company.getExternalCompanyId()).isEqualTo(EXTERNAL);
        assertThat(ranAsSuperAdmin).isTrue();
        ArgumentCaptor<CreateCompanyCommand> cmd = ArgumentCaptor.forClass(CreateCompanyCommand.class);
        verify(companyService).create(cmd.capture());
        assertThat(cmd.getValue().subscriptionPlanId()).isEqualTo(planId);
        assertThat(cmd.getValue().adminEmail()).isEqualTo("admin@acme.test");
        assertThat(cmd.getValue().companyCode()).isEqualTo("ACME");
        // The synthetic principal must not leak past the call.
        assertThat(SecurityUtils.getCurrentUser()).isEmpty();
    }

    @Test
    void passesTheProfileOnToTheNewCompany() {
        SubscriptionPlan plan = SubscriptionPlan.builder().build();
        plan.setId(UUID.randomUUID());
        when(planService.getByCode("STARTER")).thenReturn(plan);
        when(companyRepository.findByExternalCompanyId(EXTERNAL)).thenReturn(Optional.empty());
        Company company = Company.builder().companyId(UUID.randomUUID()).build();
        when(companyService.create(any())).thenReturn(
                new CompanyService.CreatedCompany(company, UUID.randomUUID(), "a@b.test", "x", true, 1, 1));

        service.provision(EXTERNAL, new PlatformTenantService.ProvisionCommand("ACME", "Acme", "a@b.test", "Asha", null,
                "+919999999999", new PlatformTenantService.Profile("IN", "https://acme.test", "1 Main Rd", null, "Pune",
                        "MH", "411001", "27ABCDE1234F1Z5", "ABCDE1234F", "https://files/logo", null, null, null)));

        ArgumentCaptor<CreateCompanyCommand> cmd = ArgumentCaptor.forClass(CreateCompanyCommand.class);
        verify(companyService).create(cmd.capture());
        assertThat(cmd.getValue().gstNumber()).isEqualTo("27ABCDE1234F1Z5");
        assertThat(cmd.getValue().panNumber()).isEqualTo("ABCDE1234F");
        assertThat(cmd.getValue().city()).isEqualTo("Pune");
        assertThat(cmd.getValue().website()).isEqualTo("https://acme.test");
        assertThat(cmd.getValue().logo()).isNull();
    }

    @Test
    void repeatedCallReturnsExistingCompanyWithoutCreating() {
        Company existing = Company.builder().companyId(UUID.randomUUID()).status(CompanyStatus.ACTIVE).build();
        when(companyRepository.findByExternalCompanyId(EXTERNAL)).thenReturn(Optional.of(existing));

        PlatformTenantService.Result result = service.provision(EXTERNAL, command("admin@acme.test", "9"));

        assertThat(result.created()).isFalse();
        assertThat(result.companyId()).isEqualTo(existing.getCompanyId());
        verify(companyService, never()).create(any());
        verify(companyService, never()).activate(any());
    }

    @Test
    void reactivatesCompanyThatWasDeactivated() {
        Company existing = Company.builder().companyId(UUID.randomUUID()).status(CompanyStatus.INACTIVE).build();
        existing.setId(UUID.randomUUID());
        when(companyRepository.findByExternalCompanyId(EXTERNAL)).thenReturn(Optional.of(existing));

        service.provision(EXTERNAL, command(null, null));

        verify(companyService).activate(existing.getId());
    }

    @Test
    void refusesToCreateWithoutAdminEmail() {
        when(companyRepository.findByExternalCompanyId(EXTERNAL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.provision(EXTERNAL, command(" ", "9")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("adminEmail");
        verify(companyService, never()).create(any());
    }

    @Test
    void refusesToCreateWithoutMobileWhenNoDefaultConfigured() {
        when(companyRepository.findByExternalCompanyId(EXTERNAL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.provision(EXTERNAL, command("a@b.test", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("mobile");
    }

    @Test
    void deprovisionDeactivatesButKeepsData() {
        Company existing = Company.builder().companyId(UUID.randomUUID()).build();
        existing.setId(UUID.randomUUID());
        when(companyRepository.findByExternalCompanyId(EXTERNAL)).thenReturn(Optional.of(existing));

        assertThat(service.deprovision(EXTERNAL)).isTrue();
        verify(companyService).deactivate(existing.getId(), "Removed by OCCPL platform");
        verify(companyService, never()).delete(any());
    }

    @Test
    void deprovisionOfUnknownCompanyIsNotFound() {
        when(companyRepository.findByExternalCompanyId(EXTERNAL)).thenReturn(Optional.empty());
        assertThat(service.deprovision(EXTERNAL)).isFalse();
    }
}
