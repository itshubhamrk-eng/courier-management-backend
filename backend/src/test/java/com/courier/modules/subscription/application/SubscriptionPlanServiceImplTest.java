package com.courier.modules.subscription.application;

import com.courier.modules.subscription.application.command.CreateSubscriptionPlanCommand;
import com.courier.modules.subscription.application.command.UpdateSubscriptionPlanCommand;
import com.courier.modules.subscription.domain.PlanType;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.modules.subscription.domain.SubscriptionPlanRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
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
 * Rules of the subscription plan module, with the repository and audit trail mocked.
 *
 * <p>The {@code @PreAuthorize} on the implementation is not exercised here — method
 * security needs a proxy, so it belongs to an integration slice. What is exercised is
 * everything the annotation guards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionPlanServiceImplTest {

    @Mock
    private SubscriptionPlanRepository repository;

    @Mock
    private AuditService auditService;

    private SubscriptionPlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPlanServiceImpl(repository, auditService);
        when(repository.save(any(SubscriptionPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.isPlanCodeTaken(any(), any())).thenReturn(false);
        when(repository.isPlanNameTaken(any(), any())).thenReturn(false);
    }

    // ------------------------------------------------------------------- create

    private CreateSubscriptionPlanCommand createCommand(PlanType type,
                                                        BigDecimal monthly,
                                                        Integer maxUsers) {
        return new CreateSubscriptionPlanCommand(
                "standard_monthly", " Standard ", "Mid tier", type,
                monthly, new BigDecimal("49990.0000"), "inr", 0,
                maxUsers, 5, 3, 10000, 50, 20, 500, 12000, 50, 600,
                Map.of("bulkBooking", true), null, 20);
    }

    @Test
    @DisplayName("create normalises the code and currency and defaults to active")
    void createNormalises() {
        SubscriptionPlan saved = service.create(
                createCommand(PlanType.STANDARD, new BigDecimal("4999.0000"), 25));

        assertThat(saved.getPlanCode()).isEqualTo("STANDARD_MONTHLY");
        assertThat(saved.getPlanName()).isEqualTo("Standard");
        assertThat(saved.getCurrency()).isEqualTo("INR");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getMaxUsers()).isEqualTo(25);
    }

    @Test
    @DisplayName("create rejects a plan code already used, including by a deleted plan")
    void createRejectsDuplicateCode() {
        // The check deliberately counts soft-deleted rows: the database unique key does
        // not know about `deleted`, so without this the caller gets an opaque 409.
        when(repository.isPlanCodeTaken(eq("STANDARD_MONTHLY"), isNull())).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                createCommand(PlanType.STANDARD, new BigDecimal("4999"), 25)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("planCode");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create rejects a duplicate plan name")
    void createRejectsDuplicateName() {
        when(repository.isPlanNameTaken(eq("Standard"), isNull())).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                createCommand(PlanType.STANDARD, new BigDecimal("4999"), 25)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("planName");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create refuses to price a TRIAL plan")
    void createRejectsPricedTrial() {
        assertThatThrownBy(() -> service.create(
                createCommand(PlanType.TRIAL, new BigDecimal("999"), 25)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("TRIAL");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create nulls every quota on an ENTERPRISE plan")
    void createMakesEnterpriseUnlimited() {
        SubscriptionPlan saved = service.create(
                createCommand(PlanType.ENTERPRISE, new BigDecimal("199999"), 25));

        assertThat(saved.getMaxUsers()).isNull();
        assertThat(saved.getMaxBranches()).isNull();
        assertThat(saved.getStorageLimitGb()).isNull();
        assertThat(saved.getApiRateLimit()).isNull();
        assertThat(saved.isUnlimited()).isTrue();
    }

    @Test
    @DisplayName("create writes an audit event carrying the code and price")
    void createIsAudited() {
        service.create(createCommand(PlanType.STANDARD, new BigDecimal("4999.0000"), 25));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(AuditAction.SUBSCRIPTION_PLAN_CREATED),
                eq("SubscriptionPlan"), any(UUID.class), details.capture());

        assertThat(details.getValue())
                .containsEntry("planCode", "STANDARD_MONTHLY")
                .containsEntry("currency", "INR");
    }

    @Test
    @DisplayName("create copies the feature flags rather than holding the caller's map")
    void createCopiesFeatureFlags() {
        Map<String, Object> caller = new java.util.HashMap<>(Map.of("bulkBooking", true));
        CreateSubscriptionPlanCommand command = new CreateSubscriptionPlanCommand(
                "basic", "Basic", null, PlanType.BASIC,
                BigDecimal.ZERO, BigDecimal.ZERO, "INR", 0,
                5, 1, 1, 100, 5, 2, 50, 1200, 5, 60,
                caller, true, 10);

        SubscriptionPlan saved = service.create(command);
        caller.put("bulkBooking", false);

        assertThat(saved.isFeatureEnabled("bulkBooking")).isTrue();
    }

    // ------------------------------------------------------------------- update

    private SubscriptionPlan existing() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .planCode("STANDARD_MONTHLY")
                .planName("Standard")
                .planType(PlanType.STANDARD)
                .monthlyPrice(new BigDecimal("4999.0000"))
                .yearlyPrice(new BigDecimal("49990.0000"))
                .currency("INR")
                .trialDays(0)
                .maxUsers(25)
                .displayOrder(20)
                .active(true)
                .build();
        plan.setVersion(3L);
        return plan;
    }

    private UpdateSubscriptionPlanCommand updateCommand(BigDecimal monthly, Long version) {
        return new UpdateSubscriptionPlanCommand(
                "Standard Plus", "Mid tier", PlanType.STANDARD,
                monthly, new BigDecimal("59990.0000"), "INR", 0,
                30, 5, 3, 10000, 50, 20, 500, 12000, 50, 600,
                Map.of(), 20, version);
    }

    @Test
    @DisplayName("update applies the new values when the version matches")
    void updateApplies() {
        SubscriptionPlan plan = existing();
        when(repository.findById(plan.getId())).thenReturn(Optional.of(plan));

        SubscriptionPlan saved = service.update(plan.getId(), updateCommand(new BigDecimal("5999"), 3L));

        assertThat(saved.getPlanName()).isEqualTo("Standard Plus");
        assertThat(saved.getMonthlyPrice()).isEqualByComparingTo("5999");
        assertThat(saved.getMaxUsers()).isEqualTo(30);
    }

    @Test
    @DisplayName("update rejects a stale version with an optimistic lock failure")
    void updateRejectsStaleVersion() {
        SubscriptionPlan plan = existing();
        when(repository.findById(plan.getId())).thenReturn(Optional.of(plan));

        // Another admin already saved: the row is at version 3, this client read 2.
        assertThatThrownBy(() -> service.update(plan.getId(), updateCommand(new BigDecimal("1"), 2L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update leaves the plan code alone")
    void updateCannotChangeCode() {
        SubscriptionPlan plan = existing();
        when(repository.findById(plan.getId())).thenReturn(Optional.of(plan));

        SubscriptionPlan saved = service.update(plan.getId(), updateCommand(new BigDecimal("5999"), 3L));

        assertThat(saved.getPlanCode()).isEqualTo("STANDARD_MONTHLY");
    }

    @Test
    @DisplayName("update excludes the row itself from the name uniqueness check")
    void updateAllowsKeepingItsOwnName() {
        SubscriptionPlan plan = existing();
        when(repository.findById(plan.getId())).thenReturn(Optional.of(plan));

        service.update(plan.getId(), updateCommand(new BigDecimal("5999"), 3L));

        verify(repository).isPlanNameTaken("Standard Plus", plan.getId());
    }

    @Test
    @DisplayName("update audits only the fields that actually changed")
    void updateAuditsOnlyChanges() {
        SubscriptionPlan plan = existing();
        when(repository.findById(plan.getId())).thenReturn(Optional.of(plan));

        service.update(plan.getId(), updateCommand(new BigDecimal("5999.0000"), 3L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(AuditAction.SUBSCRIPTION_PLAN_UPDATED),
                eq("SubscriptionPlan"), eq(plan.getId()), details.capture());

        assertThat(details.getValue()).containsKeys("planName", "monthlyPrice", "maxUsers");
        // currency and displayOrder were unchanged and must not appear.
        assertThat(details.getValue()).doesNotContainKeys("currency", "displayOrder");
    }

    @Test
    @DisplayName("update on a missing plan is a 404, not a create")
    void updateMissingPlan() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, updateCommand(new BigDecimal("1"), 0L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------- read and lifecycle

    @Test
    @DisplayName("getById surfaces a missing plan as 404")
    void getByIdMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getByCode normalises the code before looking it up")
    void getByCodeNormalises() {
        SubscriptionPlan plan = existing();
        when(repository.findByPlanCode("STANDARD_MONTHLY")).thenReturn(Optional.of(plan));

        assertThat(service.getByCode("standard_monthly")).isSameAs(plan);
    }

    @Test
    @DisplayName("search delegates to the specification executor with the given page request")
    void searchDelegates() {
        Page<SubscriptionPlan> page = new PageImpl<>(List.of(existing()));
        when(repository.findAll(any(Specification.class), eq(PageRequest.of(0, 20)))).thenReturn(page);

        assertThat(service.search(null, PageRequest.of(0, 20))).isSameAs(page);
    }

    @Test
    @DisplayName("activate is idempotent and does not audit a no-op")
    void activateIsIdempotent() {
        SubscriptionPlan plan = existing();
        when(repository.findById(plan.getId())).thenReturn(Optional.of(plan));

        service.activate(plan.getId());

        verify(repository, never()).save(any());
        verify(auditService, never()).record(eq(AuditAction.SUBSCRIPTION_PLAN_ACTIVATED),
                any(), any(), any());
    }

    @Test
    @DisplayName("activate turns an inactive plan back on and audits it")
    void activateInactivePlan() {
        SubscriptionPlan plan = existing();
        plan.deactivate();
        when(repository.findById(plan.getId())).thenReturn(Optional.of(plan));

        SubscriptionPlan saved = service.activate(plan.getId());

        assertThat(saved.isActive()).isTrue();
        verify(auditService).record(eq(AuditAction.SUBSCRIPTION_PLAN_ACTIVATED),
                eq("SubscriptionPlan"), eq(plan.getId()), any());
    }

    @Test
    @DisplayName("deactivate withdraws the plan from the catalogue")
    void deactivate() {
        SubscriptionPlan plan = existing();
        when(repository.findById(plan.getId())).thenReturn(Optional.of(plan));

        SubscriptionPlan saved = service.deactivate(plan.getId());

        assertThat(saved.isActive()).isFalse();
        verify(auditService).record(eq(AuditAction.SUBSCRIPTION_PLAN_DEACTIVATED),
                eq("SubscriptionPlan"), eq(plan.getId()), any());
    }

    @Test
    @DisplayName("delete is a soft delete: the row is flagged, never removed")
    void deleteIsSoft() {
        SubscriptionPlan plan = existing();
        when(repository.findById(plan.getId())).thenReturn(Optional.of(plan));

        service.delete(plan.getId());

        assertThat(plan.isDeleted()).isTrue();
        assertThat(plan.isActive()).isFalse();
        verify(repository).save(plan);
        // The project invariant: nothing is ever hard deleted through the service layer.
        // Typed matcher: JpaSpecificationExecutor also declares delete(Specification).
        verify(repository, never()).delete(any(SubscriptionPlan.class));
        verify(repository, never()).deleteById(any());
        verify(auditService).record(eq(AuditAction.SUBSCRIPTION_PLAN_DELETED),
                eq("SubscriptionPlan"), eq(plan.getId()), any());
    }

    @Test
    @DisplayName("delete on a missing plan is a 404")
    void deleteMissingPlan() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
