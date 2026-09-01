package com.courier.modules.company.application;

import com.courier.modules.auth.application.UserProvisioningService;
import com.courier.modules.company.application.command.CreateCompanyCommand;
import com.courier.modules.company.application.command.UpdateCompanyCommand;
import com.courier.modules.company.application.event.CompanyEvent;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanySettingRepository;
import com.courier.modules.company.domain.CompanyStatus;
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.modules.subscription.application.SubscriptionPlanService;
import com.courier.modules.subscription.domain.PlanType;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rules of the company module, with repositories, provisioning and the plan service
 * mocked.
 *
 * <p>The class-level {@code @PreAuthorize} is not exercised here — method security needs
 * a proxy and belongs to an integration slice. What is exercised is everything it guards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyServiceImplTest {

    @Mock private CompanyRepository repository;
    @Mock private CompanyRoleRepository roleRepository;
    @Mock private CompanySettingRepository settingRepository;
    @Mock private CompanyProvisioningService provisioningService;
    @Mock private SubscriptionPlanService subscriptionPlanService;
    @Mock private AuditService auditService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private FileStoragePort fileStoragePort;

    private CompanyServiceImpl service;
    private SubscriptionPlan plan;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        service = new CompanyServiceImpl(repository, roleRepository, settingRepository,
                provisioningService, subscriptionPlanService, auditService, eventPublisher, fileStoragePort);

        plan = SubscriptionPlan.builder()
                .planCode("STANDARD_MONTHLY")
                .planName("Standard")
                .planType(PlanType.STANDARD)
                .monthlyPrice(new BigDecimal("4999.0000"))
                .yearlyPrice(new BigDecimal("49990.0000"))
                .currency("INR")
                .trialDays(14)
                .maxUsers(25)
                .active(true)
                .build();

        adminUserId = UUID.randomUUID();

        when(subscriptionPlanService.getById(any())).thenReturn(plan);
        when(repository.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.isCompanyCodeTaken(any(), any())).thenReturn(false);
        when(repository.isEmailTaken(any(), any())).thenReturn(false);
        when(repository.isGstNumberTaken(any(), any())).thenReturn(false);
        when(repository.isPanNumberTaken(any(), any())).thenReturn(false);
        when(repository.isCompanyIdTaken(any())).thenReturn(false);
        when(provisioningService.provision(any(), any(), any(), any(), any(), any()))
                .thenReturn(new CompanyProvisioningService.ProvisioningResult(5, 25,
                        new UserProvisioningService.ProvisionedUser(
                                adminUserId, "ops@acme.test", "Temp@Pass123", true)));
    }

    @AfterEach
    void tearDown() {
        // The service binds companies with runAs; a leak here would corrupt the next test.
        CompanyContext.clear();
    }

    private CreateCompanyCommand createCommand() {
        return new CreateCompanyCommand(
                "acme_logistics", "Acme Logistics", "Acme Logistics Pvt Ltd", null,
                plan.getId(), "OPS@Acme.test", "+91 9876543210", null, null,
                null, null, null, null, null,
                "1 Main Road", null, "India", "MH", "Pune", "411001",
                null, null, null, null, null, null, null,
                null, "Ops", "Admin", null);
    }

    // ------------------------------------------------------------------- create

    @Test
    @DisplayName("create generates a company id distinct from the company id")
    void createGeneratesCompanyId() {
        CompanyService.CreatedCompany created = service.create(createCommand());

        assertThat(created.company().getCompanyId()).isNotNull();
        assertThat(created.company().getCompanyId()).isNotEqualTo(created.company().getId());
    }

    @Test
    @DisplayName("create normalises the code and email and defaults localisation from the plan")
    void createNormalises() {
        Company saved = service.create(createCommand()).company();

        assertThat(saved.getCompanyCode()).isEqualTo("ACME_LOGISTICS");
        assertThat(saved.getEmail()).isEqualTo("ops@acme.test");
        assertThat(saved.getCurrency()).isEqualTo("INR");
        assertThat(saved.getTimezone()).isEqualTo(Company.DEFAULT_TIMEZONE);
    }

    @Test
    @DisplayName("a plan with trial days starts the company in TRIAL with a dated window")
    void createStartsTrial() {
        Company saved = service.create(createCommand()).company();

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.TRIAL);
        assertThat(saved.getTrialStartDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getTrialEndDate()).isEqualTo(LocalDate.now().plusDays(14));
        // Billing starts when the trial ends, not on signup.
        assertThat(saved.getSubscriptionStartDate()).isEqualTo(LocalDate.now().plusDays(14));
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @DisplayName("a plan without trial days starts the company ACTIVE and leaves no trial window")
    void createWithoutTrial() {
        plan.setTrialDays(0);

        Company saved = service.create(createCommand()).company();

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(saved.getTrialStartDate()).isNull();
        assertThat(saved.getTrialEndDate()).isNull();
        assertThat(saved.getSubscriptionStartDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("create provisions roles, settings and an admin, and reports the counts")
    void createProvisions() {
        CompanyService.CreatedCompany created = service.create(createCommand());

        verify(provisioningService).provision(any(Company.class), eq(plan),
                eq("ops@acme.test"), eq("Ops"), eq("Admin"), eq("+91 9876543210"));
        assertThat(created.roleCount()).isEqualTo(5);
        assertThat(created.settingCount()).isEqualTo(25);
        assertThat(created.adminUserId()).isEqualTo(adminUserId);
        assertThat(created.activationEmailSent()).isTrue();
        // The temporary password reaches the caller exactly once, here.
        assertThat(created.temporaryPassword()).isEqualTo("Temp@Pass123");
    }

    @Test
    @DisplayName("the admin address defaults to the company email when none is given")
    void adminEmailDefaultsToCompanyEmail() {
        service.create(createCommand());

        verify(provisioningService).provision(any(), any(), eq("ops@acme.test"), any(), any(), any());
    }

    @Test
    @DisplayName("an explicit admin address is used instead")
    void explicitAdminEmail() {
        CreateCompanyCommand command = new CreateCompanyCommand(
                "acme2", "Acme Two", null, null, plan.getId(), "billing@acme.test",
                "+91 9876543210", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                "Admin@Acme.test", null, null, null);

        service.create(command);

        verify(provisioningService).provision(any(), any(), eq("admin@acme.test"), any(), any(), any());
    }

    @Test
    @DisplayName("create rejects a duplicate company code, including one held by a deleted company")
    void rejectsDuplicateCode() {
        when(repository.isCompanyCodeTaken(eq("ACME_LOGISTICS"), isNull())).thenReturn(true);

        assertThatThrownBy(() -> service.create(createCommand()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("companyCode");

        verify(repository, never()).save(any());
        verify(provisioningService, never()).provision(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("create rejects a duplicate email, GST or PAN")
    void rejectsDuplicateIdentifiers() {
        when(repository.isEmailTaken(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(createCommand()))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("email");

        when(repository.isEmailTaken(any(), any())).thenReturn(false);
        when(repository.isGstNumberTaken(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(createCommand()))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("gstNumber");

        when(repository.isGstNumberTaken(any(), any())).thenReturn(false);
        when(repository.isPanNumberTaken(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(createCommand()))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("panNumber");
    }

    @Test
    @DisplayName("create refuses an inactive subscription plan")
    void rejectsInactivePlan() {
        plan.deactivate();

        assertThatThrownBy(() -> service.create(createCommand()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not active");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create refuses a missing plan id rather than failing at flush time")
    void rejectsMissingPlanId() {
        CreateCompanyCommand command = new CreateCompanyCommand(
                "acme3", "Acme Three", null, null, null, "three@acme.test", "+91 9876543210",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("subscription plan is required");
    }

    @Test
    @DisplayName("create retries when a generated company id already exists")
    void retriesOnCompanyIdCollision() {
        // A collision would merge two companies' data — the worst failure this system
        // can have — so the generated id is checked rather than assumed.
        when(repository.isCompanyIdTaken(any())).thenReturn(true, false);

        Company saved = service.create(createCommand()).company();

        assertThat(saved.getCompanyId()).isNotNull();
        verify(repository, org.mockito.Mockito.times(2)).isCompanyIdTaken(any());
    }

    @Test
    @DisplayName("create audits and publishes CompanyCreated")
    void createEmitsAuditAndEvent() {
        service.create(createCommand());

        verify(auditService).record(eq(AuditAction.COMPANY_CREATED), eq("Company"),
                any(UUID.class), any());

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(CompanyEvent.CompanyCreated.class);
        assertThat(((CompanyEvent.CompanyCreated) event.getValue()).adminUserId())
                .isEqualTo(adminUserId);
    }

    // ------------------------------------------------------------------- update

    private Company existing() {
        Company company = Company.builder()
                .companyId(Company.newCompanyId())
                .companyCode("ACME_LOGISTICS")
                .companyName("Acme Logistics")
                .subscriptionPlanId(plan.getId())
                .status(CompanyStatus.ACTIVE)
                .email("ops@acme.test")
                .mobile("+91 9876543210")
                .city("Pune")
                .build();
        company.applyInvariants();
        company.setVersion(2L);
        return company;
    }

    private UpdateCompanyCommand updateCommand(String name, Long version) {
        return new UpdateCompanyCommand(
                name, null, null, plan.getId(), "ops@acme.test", "+91 9876543210",
                null, null, null, null, null, null, null, null, null,
                "India", "MH", "Mumbai", "400001", null, null, null, null, null, null,
                null, null, null, version);
    }

    @Test
    @DisplayName("update applies changes when the version matches")
    void updateApplies() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.update(company.getId(), updateCommand("Acme Logistics India", 2L));

        assertThat(saved.getCompanyName()).isEqualTo("Acme Logistics India");
        assertThat(saved.getCity()).isEqualTo("Mumbai");
    }

    @Test
    @DisplayName("update rejects a stale version")
    void updateRejectsStaleVersion() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.update(company.getId(), updateCommand("Nope", 1L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update cannot change the company code, company id or status")
    void updateCannotChangeImmutables() {
        Company company = existing();
        UUID companyId = company.getCompanyId();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.update(company.getId(), updateCommand("Renamed", 2L));

        assertThat(saved.getCompanyCode()).isEqualTo("ACME_LOGISTICS");
        assertThat(saved.getCompanyId()).isEqualTo(companyId);
        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
    }

    @Test
    @DisplayName("update audits only the fields that changed")
    void updateAuditsOnlyChanges() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        service.update(company.getId(), updateCommand("Acme Logistics India", 2L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(AuditAction.COMPANY_UPDATED), eq("Company"),
                eq(company.getId()), details.capture());

        assertThat(details.getValue()).containsKeys("companyName", "city");
        assertThat(details.getValue()).doesNotContainKeys("email", "mobile");
    }

    @Test
    @DisplayName("update on a missing company is a 404, not a create")
    void updateMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, updateCommand("X", 0L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("suspend requires a reason")
    void suspendRequiresReason() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.suspend(company.getId(), "  "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reason is required");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("suspend blocks the company and records the reason")
    void suspend() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.suspend(company.getId(), "Non-payment of INV-42");

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getRemarks()).isEqualTo("Non-payment of INV-42");
        verify(auditService).record(eq(AuditAction.COMPANY_SUSPENDED), eq("Company"),
                eq(company.getId()), any());
    }

    @Test
    @DisplayName("activate brings a suspended company back and publishes the event")
    void activate() {
        Company company = existing();
        company.transitionTo(CompanyStatus.SUSPENDED);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.activate(company.getId());

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(saved.isActive()).isTrue();

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(CompanyEvent.CompanyActivated.class);
    }

    @Test
    @DisplayName("activate is idempotent and emits nothing for a no-op")
    void activateIdempotent() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        service.activate(company.getId());

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(auditService, never()).record(eq(AuditAction.COMPANY_ACTIVATED), any(), any(), any());
    }

    @Test
    @DisplayName("expire closes the window and stamps an end date when none was set")
    void expire() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.expire(company.getId());

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.EXPIRED);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getSubscriptionEndDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("suspending an expired company is refused by the lifecycle")
    void illegalTransitionRefused() {
        Company company = existing();
        company.transitionTo(CompanyStatus.EXPIRED);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.suspend(company.getId(), "late payment"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot move from EXPIRED to SUSPENDED");
    }

    @Test
    @DisplayName("delete is soft, deactivates, and never hard-deletes")
    void deleteIsSoft() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        service.delete(company.getId());

        assertThat(company.isDeleted()).isTrue();
        assertThat(company.isActive()).isFalse();
        verify(repository).save(company);
        // Typed matcher: JpaSpecificationExecutor also declares delete(Specification).
        verify(repository, never()).delete(any(Company.class));
        verify(repository, never()).deleteById(any());
        verify(auditService).record(eq(AuditAction.COMPANY_DELETED), eq("Company"),
                eq(company.getId()), any());
    }

    // --------------------------------------------------------------------- read

    @Test
    @DisplayName("search delegates to the specification executor")
    void searchDelegates() {
        Page<Company> page = new PageImpl<>(List.of(existing()));
        when(repository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(page);

        assertThat(service.search(null, PageRequest.of(0, 20))).isSameAs(page);
    }

    @Test
    @DisplayName("getByCompanyId surfaces an unknown company as 404")
    void getByCompanyIdMissing() {
        UUID companyId = UUID.randomUUID();
        when(repository.findByCompanyId(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByCompanyId(companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listRoles binds the company's company before reading company-owned rows")
    void listRolesBindsCompany() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));
        when(roleRepository.findAllByOrderByRoleCodeAsc()).thenAnswer(invocation -> {
            // Without the binding the Hibernate filter has nothing to apply and the
            // query would return every company's roles.
            assertThat(CompanyContext.getCompanyId()).contains(company.getCompanyId());
            return List.of();
        });

        service.listRoles(company.getId());

        verify(roleRepository).findAllByOrderByRoleCodeAsc();
        assertThat(CompanyContext.isSet()).isFalse();
    }

    @Test
    @DisplayName("listSettings binds the company too, and restores the previous binding")
    void listSettingsBindsCompany() {
        Company company = existing();
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));
        when(settingRepository.findAllByOrderByCategoryAscSettingKeyAsc()).thenAnswer(invocation -> {
            assertThat(CompanyContext.getCompanyId()).contains(company.getCompanyId());
            return List.of();
        });

        service.listSettings(company.getId());

        assertThat(CompanyContext.isSet()).isFalse();
    }
}
