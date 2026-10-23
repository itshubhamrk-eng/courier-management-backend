package com.courier.modules.customer.application.event;

import com.courier.modules.customer.domain.CustomerType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published by Customer. Consumed {@code @TransactionalEventListener(AFTER_COMMIT)},
 * in-process, so a rolled-back customer triggers nothing.
 */
public sealed interface CustomerEvent {

    UUID customerId();

    UUID companyId();

    record CustomerCreated(UUID customerId, UUID companyId, CustomerType customerType,
                           Instant occurredAt) implements CustomerEvent {
    }
}
