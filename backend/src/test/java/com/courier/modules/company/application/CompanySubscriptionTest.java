package com.courier.modules.company.application;

import com.courier.modules.company.application.command.AssignSubscriptionCommand;
import com.courier.modules.company.application.command.RenewSubscriptionCommand;
import com.courier.modules.company.application.event.CompanyEvent;
import com.courier.modules.company.domain.Company;
import com.courier.modules.company.domain.CompanyRepository;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanySettingRepository;
import com.courier.modules.company.domain.CompanyStatus;
import com.courier.modules.shipment.application.storage.FileStoragePort;
import com.courier.modules.subscription.application.SubscriptionPlanService;
import com.courier.modules.subscription.domain.BillingCycle;
import com.courier.modules.subscription.domain.PlanType;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three commercial acts on a company's subscription, and deactivation.
 *
 * <p>Kept apart from {@code CompanyServiceImplTest}, which covers the company record
 * itself. The distinction is the point of the endpoints existing at all: editing a
 * company and putting it on a plan are two different events, and an audit trail that
 * cannot tell them apart cannot answer "when did they move up to ENTERPRISE".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanySubscriptionTest {

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
                .active(true)
                .build();

        when(subscriptionPlanService.getById(any())).thenReturn(plan);
        when(repository.save(any(Company.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------------------------------------------- assign

    @Test
    @DisplayName("assigning a plan opens a paid window and activates the company")
    void assignActivates() {
        Company company = existing(CompanyStatus.TRIAL);
        company.setTrialEndDate(LocalDate.now().plusDays(5));
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.assignSubscription(company.getId(), new AssignSubscriptionCommand(
                plan.getId(), BillingCycle.YEARLY, 1, LocalDate.of(2026, 8, 1), null, "PO-77"));

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(saved.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(saved.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2027, 8, 1));
        assertThat(saved.getRemarks()).isEqualTo("PO-77");
    }

    @Test
    @DisplayName("assigning a paid plan closes the trial window")
    void assignEndsTheTrial() {
        // Two open windows and no rule about which one is in force is not a state worth
        // having: every expiry report would have to guess.
        Company company = existing(CompanyStatus.TRIAL);
        company.setTrialStartDate(LocalDate.now());
        company.setTrialEndDate(LocalDate.now().plusDays(14));
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.assignSubscription(company.getId(), new AssignSubscriptionCommand(
                plan.getId(), BillingCycle.MONTHLY, 1, null, null, null));

        assertThat(saved.getTrialEndDate()).isNull();
        assertThat(saved.getTrialStartDate()).isNull();
    }

    @Test
    @DisplayName("an explicit end date overrides the billing cycle")
    void explicitEndDateWins() {
        // Real contracts do not always land on a cycle boundary, and forcing one would
        // make the system disagree with the invoice.
        Company company = existing(CompanyStatus.ACTIVE);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.assignSubscription(company.getId(), new AssignSubscriptionCommand(
                plan.getId(), BillingCycle.MONTHLY, 1,
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 3, 15), null));

        assertThat(saved.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2027, 3, 15));
    }

    @Test
    @DisplayName("a window that ends before it starts is refused")
    void backwardsWindowRefused() {
        Company company = existing(CompanyStatus.ACTIVE);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.assignSubscription(company.getId(),
                new AssignSubscriptionCommand(plan.getId(), null, 1,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must end after it starts");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("an inactive plan cannot be assigned")
    void inactivePlanRefused() {
        plan.setActive(false);
        Company company = existing(CompanyStatus.ACTIVE);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.assignSubscription(company.getId(),
                new AssignSubscriptionCommand(plan.getId(), BillingCycle.MONTHLY, 1, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not active");
    }

    // -------------------------------------------------------------------- renew

    @Test
    @DisplayName("renewing early extends from the current end, not from today")
    void renewEarlyKeepsPaidDays() {
        // A customer who pays a week early must keep the week they already bought.
        Company company = existing(CompanyStatus.ACTIVE);
        LocalDate currentEnd = LocalDate.now().plusDays(20);
        company.setSubscriptionStartDate(LocalDate.now().minusMonths(11));
        company.setSubscriptionEndDate(currentEnd);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.renewSubscription(company.getId(),
                new RenewSubscriptionCommand(null, BillingCycle.YEARLY, 1, null, "INV-9"));

        assertThat(saved.getSubscriptionEndDate()).isEqualTo(currentEnd.plusYears(1));
    }

    @Test
    @DisplayName("renewing late extends from today, not from the lapsed end")
    void renewLateDoesNotBillTheGap() {
        // ...and one who pays a month late is not charged for the month they could not use.
        Company company = existing(CompanyStatus.EXPIRED);
        company.setSubscriptionStartDate(LocalDate.now().minusMonths(13));
        company.setSubscriptionEndDate(LocalDate.now().minusMonths(1));
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.renewSubscription(company.getId(),
                new RenewSubscriptionCommand(null, BillingCycle.MONTHLY, 1, null, null));

        assertThat(saved.getSubscriptionEndDate()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    @DisplayName("a renewal brings an expired company back without a second call")
    void renewReactivates() {
        Company company = existing(CompanyStatus.EXPIRED);
        company.setSubscriptionEndDate(LocalDate.now().minusDays(3));
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.renewSubscription(company.getId(),
                new RenewSubscriptionCommand(null, BillingCycle.MONTHLY, 3, null, null));

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(saved.getSubscriptionEndDate()).isEqualTo(LocalDate.now().plusMonths(3));
    }

    @Test
    @DisplayName("a renewal that would not extend the window is refused")
    void renewMustExtend() {
        Company company = existing(CompanyStatus.ACTIVE);
        LocalDate currentEnd = LocalDate.now().plusYears(1);
        company.setSubscriptionEndDate(currentEnd);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.renewSubscription(company.getId(),
                new RenewSubscriptionCommand(null, null, 1, currentEnd.minusDays(1), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must extend");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a renewal may carry an upgrade, and records one event not two")
    void renewCanUpgrade() {
        SubscriptionPlan enterprise = SubscriptionPlan.builder()
                .planCode("ENTERPRISE_YEARLY").planName("Enterprise")
                .planType(PlanType.ENTERPRISE)
                .monthlyPrice(new BigDecimal("19999.0000"))
                .yearlyPrice(new BigDecimal("199990.0000"))
                .currency("INR").trialDays(0).active(true).build();
        UUID enterpriseId = UUID.randomUUID();
        when(subscriptionPlanService.getById(enterpriseId)).thenReturn(enterprise);

        Company company = existing(CompanyStatus.ACTIVE);
        company.setSubscriptionEndDate(LocalDate.now().plusDays(5));
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.renewSubscription(company.getId(),
                new RenewSubscriptionCommand(enterpriseId, BillingCycle.YEARLY, 1, null, null));

        assertThat(saved.getSubscriptionPlanId()).isEqualTo(enterprise.getId());
        // The customer experiences one event, so the trail records one.
        verify(auditService).record(eq(AuditAction.COMPANY_SUBSCRIPTION_RENEWED),
                eq("Company"), any(), anyMap());
        verify(auditService, never()).record(eq(AuditAction.COMPANY_SUBSCRIPTION_ASSIGNED),
                any(), any(), anyMap());
    }

    // ------------------------------------------------------------------ suspend

    @Test
    @DisplayName("suspending a subscription closes the paid window as of today")
    void suspendClosesTheWindow() {
        // Left open, the company would keep reading as "paid until December" on every
        // renewals report while being unable to sign in.
        Company company = existing(CompanyStatus.ACTIVE);
        company.setSubscriptionEndDate(LocalDate.now().plusMonths(6));
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.suspendSubscription(company.getId(), "Chargeback on INV-42");

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
        assertThat(saved.getSubscriptionEndDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getRemarks()).isEqualTo("Chargeback on INV-42");
    }

    @Test
    @DisplayName("suspending a subscription requires a reason")
    void suspendNeedsAReason() {
        assertThatThrownBy(() -> service.suspendSubscription(UUID.randomUUID(), "  "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reason is required");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a lapsed window is not pushed forward by a suspension")
    void suspendDoesNotExtendALapsedWindow() {
        Company company = existing(CompanyStatus.ACTIVE);
        LocalDate lapsed = LocalDate.now().minusDays(10);
        company.setSubscriptionEndDate(lapsed);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.suspendSubscription(company.getId(), "Non-payment");

        assertThat(saved.getSubscriptionEndDate()).isEqualTo(lapsed);
    }

    // --------------------------------------------------------------- deactivate

    @Test
    @DisplayName("deactivation is its own event, distinct from suspension")
    void deactivateIsNotSuspension() {
        Company company = existing(CompanyStatus.ACTIVE);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.deactivate(company.getId(), "No bookings since March");

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.INACTIVE);
        verify(auditService).record(eq(AuditAction.COMPANY_DEACTIVATED), eq("Company"), any(), anyMap());
        verify(auditService, never()).record(eq(AuditAction.COMPANY_SUSPENDED), any(), any(), anyMap());

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(CompanyEvent.CompanyDeactivated.class);
    }

    @Test
    @DisplayName("deactivation needs no reason and is idempotent")
    void deactivateIsIdempotentAndReasonless() {
        Company company = existing(CompanyStatus.INACTIVE);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company saved = service.deactivate(company.getId(), null);

        assertThat(saved.getStatus()).isEqualTo(CompanyStatus.INACTIVE);
        // No audit noise, no event, no write for a no-op.
        verify(repository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("a suspended company can still be deactivated")
    void deactivateIsLegalFromEveryOtherStatus() {
        // An operator switching off a dormant company should not first have to work out
        // whether its trial lapsed or its card bounced.
        for (CompanyStatus from : new CompanyStatus[]{
                CompanyStatus.TRIAL, CompanyStatus.ACTIVE,
                CompanyStatus.SUSPENDED, CompanyStatus.EXPIRED}) {
            Company company = existing(from);
            when(repository.findById(company.getId())).thenReturn(Optional.of(company));

            assertThat(service.deactivate(company.getId(), null).getStatus())
                    .as("deactivating from %s", from)
                    .isEqualTo(CompanyStatus.INACTIVE);
        }
    }

    private Company existing(CompanyStatus status) {
        Company company = Company.builder()
                .companyId(Company.newCompanyId())
                .companyCode("ACME_LOGISTICS")
                .companyName("Acme Logistics")
                .subscriptionPlanId(plan.getId())
                .status(status)
                .email("ops@acme.test")
                .mobile("+91 9876543210")
                .city("Pune")
                .build();
        company.applyInvariants();
        company.setVersion(2L);
        return company;
    }
}
