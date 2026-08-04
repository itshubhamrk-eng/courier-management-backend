package com.courier.modules.subscription.application;

import com.courier.modules.subscription.application.command.CreateSubscriptionPlanCommand;
import com.courier.modules.subscription.application.command.UpdateSubscriptionPlanCommand;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.modules.subscription.domain.SubscriptionPlanCriteria;
import com.courier.modules.subscription.domain.SubscriptionPlanRepository;
import com.courier.modules.subscription.domain.SubscriptionPlanSpecifications;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Subscription plan use cases.
 *
 * <p>The class-level {@code @PreAuthorize} is the authoritative access control for this
 * module: every public method requires {@code SUPER_ADMIN}, including the read paths —
 * pricing and quota structure is commercial information, not public catalogue data.
 * {@code SecurityConfig}'s URL rule is a duplicate outer gate, deliberately, so that a
 * future controller added under the same path cannot accidentally ship unguarded.
 *
 * <p>The role string is assembled from {@link Roles#SUPER_ADMIN} by compile-time
 * constant folding, so a rename of the constant cannot leave a stale literal behind
 * in a SpEL expression the compiler never sees.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Roles.SUPER_ADMIN + "')")
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private static final String ENTITY = "SubscriptionPlan";

    private final SubscriptionPlanRepository repository;
    private final AuditService auditService;

    @Override
    @Transactional
    public SubscriptionPlan create(CreateSubscriptionPlanCommand command) {
        String planCode = SubscriptionPlan.normaliseCode(command.planCode());
        String planName = command.planName() == null ? null : command.planName().trim();

        requireCodeAvailable(planCode, null);
        requireNameAvailable(planName, null);

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .planCode(planCode)
                .planName(planName)
                .description(command.description())
                .planType(command.planType())
                .monthlyPrice(command.monthlyPrice())
                .yearlyPrice(command.yearlyPrice())
                .currency(SubscriptionPlan.normaliseCurrency(command.currency()))
                .trialDays(command.trialDays() == null ? 0 : command.trialDays())
                .maxUsers(command.maxUsers())
                .maxBranches(command.maxBranches())
                .maxHubs(command.maxHubs())
                .maxCustomers(command.maxCustomers())
                .maxDrivers(command.maxDrivers())
                .maxVehicles(command.maxVehicles())
                .maxDailyBookings(command.maxDailyBookings())
                .maxMonthlyBookings(command.maxMonthlyBookings())
                .storageLimitGb(command.storageLimitGb())
                .apiRateLimit(command.apiRateLimit())
                .featureFlags(copyFlags(command.featureFlags()))
                .active(command.active() == null || command.active())
                .displayOrder(command.displayOrder() == null ? 0 : command.displayOrder())
                .build();

        // Throws if the tier's own rules cannot be satisfied, and nulls every quota
        // on an ENTERPRISE plan.
        plan.applyTypeInvariants();

        SubscriptionPlan saved = repository.save(plan);

        log.info("Subscription plan {} ({}) created by {}",
                saved.getPlanCode(), saved.getId(), currentActor());
        auditService.record(AuditAction.SUBSCRIPTION_PLAN_CREATED, ENTITY, saved.getId(),
                Map.of("planCode", saved.getPlanCode(),
                        "planType", saved.getPlanType().name(),
                        "monthlyPrice", saved.getMonthlyPrice().toPlainString(),
                        "currency", saved.getCurrency()));

        return saved;
    }

    @Override
    @Transactional
    public SubscriptionPlan update(UUID id, UpdateSubscriptionPlanCommand command) {
        SubscriptionPlan plan = loadOrThrow(id);
        requireCurrentVersion(plan, command.expectedVersion());

        String planName = command.planName() == null ? null : command.planName().trim();
        requireNameAvailable(planName, id);

        Map<String, Object> before = snapshot(plan);

        plan.setPlanName(planName);
        plan.setDescription(command.description());
        plan.setPlanType(command.planType());
        plan.setMonthlyPrice(command.monthlyPrice());
        plan.setYearlyPrice(command.yearlyPrice());
        plan.setCurrency(SubscriptionPlan.normaliseCurrency(command.currency()));
        plan.setTrialDays(command.trialDays() == null ? 0 : command.trialDays());
        plan.setMaxUsers(command.maxUsers());
        plan.setMaxBranches(command.maxBranches());
        plan.setMaxHubs(command.maxHubs());
        plan.setMaxCustomers(command.maxCustomers());
        plan.setMaxDrivers(command.maxDrivers());
        plan.setMaxVehicles(command.maxVehicles());
        plan.setMaxDailyBookings(command.maxDailyBookings());
        plan.setMaxMonthlyBookings(command.maxMonthlyBookings());
        plan.setStorageLimitGb(command.storageLimitGb());
        plan.setApiRateLimit(command.apiRateLimit());
        plan.setFeatureFlags(copyFlags(command.featureFlags()));
        plan.setDisplayOrder(command.displayOrder() == null ? 0 : command.displayOrder());

        plan.applyTypeInvariants();

        SubscriptionPlan saved = repository.save(plan);

        log.info("Subscription plan {} ({}) updated by {}",
                saved.getPlanCode(), saved.getId(), currentActor());
        auditService.record(AuditAction.SUBSCRIPTION_PLAN_UPDATED, ENTITY, saved.getId(),
                changeDetails(before, snapshot(saved)));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlan getById(UUID id) {
        return loadOrThrow(id);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlan getByCode(String planCode) {
        String normalised = SubscriptionPlan.normaliseCode(planCode);
        return repository.findByPlanCode(normalised)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, normalised));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionPlan> search(SubscriptionPlanCriteria criteria, Pageable pageable) {
        return repository.findAll(SubscriptionPlanSpecifications.matching(criteria), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlan> listAssignable() {
        return repository.findAllByActiveTrueOrderByDisplayOrderAscPlanCodeAsc();
    }

    @Override
    @Transactional
    public SubscriptionPlan activate(UUID id) {
        SubscriptionPlan plan = loadOrThrow(id);
        if (plan.isActive()) {
            // Idempotent: re-activating an active plan is a no-op, and emitting an audit
            // event for it would bury the real activations in noise.
            return plan;
        }

        plan.activate();
        SubscriptionPlan saved = repository.save(plan);

        log.info("Subscription plan {} activated by {}", saved.getPlanCode(), currentActor());
        auditService.record(AuditAction.SUBSCRIPTION_PLAN_ACTIVATED, ENTITY, saved.getId(),
                Map.of("planCode", saved.getPlanCode()));

        return saved;
    }

    @Override
    @Transactional
    public SubscriptionPlan deactivate(UUID id) {
        SubscriptionPlan plan = loadOrThrow(id);
        if (!plan.isActive()) {
            return plan;
        }

        plan.deactivate();
        SubscriptionPlan saved = repository.save(plan);

        // Existing subscribers keep their plan; deactivation only withdraws it from the
        // catalogue offered to new companies.
        log.info("Subscription plan {} deactivated by {}", saved.getPlanCode(), currentActor());
        auditService.record(AuditAction.SUBSCRIPTION_PLAN_DEACTIVATED, ENTITY, saved.getId(),
                Map.of("planCode", saved.getPlanCode()));

        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        SubscriptionPlan plan = loadOrThrow(id);

        // Soft delete only — MEMORY/AI_CONTEXT.md invariant: nothing is ever hard
        // deleted through the service layer. Deactivating alongside keeps the row out
        // of the assignable catalogue even if it is later restored by hand.
        plan.deactivate();
        plan.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.save(plan);

        log.info("Subscription plan {} ({}) soft deleted by {}",
                plan.getPlanCode(), plan.getId(), currentActor());
        auditService.record(AuditAction.SUBSCRIPTION_PLAN_DELETED, ENTITY, plan.getId(),
                Map.of("planCode", plan.getPlanCode()));
    }

    // -------------------------------------------------------------------- helpers

    private SubscriptionPlan loadOrThrow(UUID id) {
        // Safe to load by primary key: this entity is platform-level, so unlike a
        // company-owned entity there is no company filter for findById to bypass.
        // @SQLRestriction still hides soft-deleted rows.
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    /**
     * Uniqueness is checked against soft-deleted rows too, because the database unique
     * key does not know about {@code deleted}. Without this the caller would get an
     * opaque 409 from the constraint instead of a message naming the field.
     */
    private void requireCodeAvailable(String planCode, UUID excludeId) {
        if (repository.isPlanCodeTaken(planCode, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "planCode", planCode);
        }
    }

    private void requireNameAvailable(String planName, UUID excludeId) {
        if (repository.isPlanNameTaken(planName, excludeId)) {
            throw new DuplicateResourceException(ENTITY, "planName", planName);
        }
    }

    /**
     * Explicit optimistic-lock check.
     *
     * <p>{@code @Version} alone would only catch a conflict between the load and the
     * flush inside <em>this</em> transaction. What actually happens is a read-modify-write
     * across two requests: two admins open the same plan, both save, and the second
     * silently overwrites the first. Comparing the version the client last saw closes
     * that window. The exception type is the one Spring already maps to
     * {@code 409 CONCURRENT_MODIFICATION} in {@code GlobalExceptionHandler}.
     */
    private void requireCurrentVersion(SubscriptionPlan plan, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        if (!Objects.equals(plan.getVersion(), expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(SubscriptionPlan.class, plan.getId());
        }
    }

    /** Defensive copy: the command's map must not become the entity's live state. */
    private Map<String, Object> copyFlags(Map<String, Object> flags) {
        return flags == null ? new LinkedHashMap<>() : new LinkedHashMap<>(flags);
    }

    private Map<String, Object> snapshot(SubscriptionPlan plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("planName", plan.getPlanName());
        values.put("planType", plan.getPlanType() == null ? null : plan.getPlanType().name());
        values.put("monthlyPrice", plan.getMonthlyPrice() == null
                ? null : plan.getMonthlyPrice().toPlainString());
        values.put("yearlyPrice", plan.getYearlyPrice() == null
                ? null : plan.getYearlyPrice().toPlainString());
        values.put("currency", plan.getCurrency());
        values.put("trialDays", plan.getTrialDays());
        values.put("maxUsers", plan.getMaxUsers());
        values.put("maxBranches", plan.getMaxBranches());
        values.put("maxHubs", plan.getMaxHubs());
        values.put("maxCustomers", plan.getMaxCustomers());
        values.put("maxDrivers", plan.getMaxDrivers());
        values.put("maxVehicles", plan.getMaxVehicles());
        values.put("maxDailyBookings", plan.getMaxDailyBookings());
        values.put("maxMonthlyBookings", plan.getMaxMonthlyBookings());
        values.put("storageLimitGb", plan.getStorageLimitGb());
        values.put("apiRateLimit", plan.getApiRateLimit());
        values.put("featureFlags", plan.getFeatureFlags());
        values.put("displayOrder", plan.getDisplayOrder());
        return values;
    }

    /**
     * Only what actually changed reaches the audit trail. A full before/after dump of
     * twenty fields per edit makes the trail unreadable and stores the same values over
     * and over.
     */
    private Map<String, Object> changeDetails(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        before.forEach((field, oldValue) -> {
            Object newValue = after.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> pair = new HashMap<>();
                pair.put("from", oldValue);
                pair.put("to", newValue);
                changes.put(field, pair);
            }
        });
        return changes;
    }

    private String currentActor() {
        return SecurityUtils.getCurrentUser()
                .map(user -> user.email() == null ? user.userId().toString() : user.email())
                .orElse("system");
    }
}
