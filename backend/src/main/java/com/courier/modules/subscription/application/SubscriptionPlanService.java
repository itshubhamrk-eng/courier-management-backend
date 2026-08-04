package com.courier.modules.subscription.application;

import com.courier.modules.subscription.application.command.CreateSubscriptionPlanCommand;
import com.courier.modules.subscription.application.command.UpdateSubscriptionPlanCommand;
import com.courier.modules.subscription.domain.SubscriptionPlan;
import com.courier.modules.subscription.domain.SubscriptionPlanCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Use cases for the subscription plan catalogue.
 *
 * <p><b>Every method requires {@code SUPER_ADMIN}.</b> The check is a class-level
 * {@code @PreAuthorize} on {@link SubscriptionPlanServiceImpl} — on the implementation
 * rather than here, so it holds whichever proxy strategy Spring picks. The URL rule in
 * {@code SecurityConfig} is only a coarse first gate.
 *
 * <p>Returns entities, not DTOs: mapping to the wire format belongs to the {@code api}
 * layer, which owns the response contract.
 */
public interface SubscriptionPlanService {

    SubscriptionPlan create(CreateSubscriptionPlanCommand command);

    /** Full replacement. Fails with 409 if {@code expectedVersion} is stale. */
    SubscriptionPlan update(UUID id, UpdateSubscriptionPlanCommand command);

    SubscriptionPlan getById(UUID id);

    SubscriptionPlan getByCode(String planCode);

    Page<SubscriptionPlan> search(SubscriptionPlanCriteria criteria, Pageable pageable);

    /** Plans assignable to a new company, in display order. */
    List<SubscriptionPlan> listAssignable();

    SubscriptionPlan activate(UUID id);

    SubscriptionPlan deactivate(UUID id);

    /** Soft delete. The row is retained; nothing is ever physically removed. */
    void delete(UUID id);
}
