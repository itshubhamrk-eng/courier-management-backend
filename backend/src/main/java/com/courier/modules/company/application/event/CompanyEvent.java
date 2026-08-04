package com.courier.modules.company.application.event;

import com.courier.modules.company.domain.CompanyStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain events published by the company module.
 *
 * <p>Published through Spring's {@code ApplicationEventPublisher} and consumed with
 * {@code @TransactionalEventListener(AFTER_COMMIT)}, so a listener never reacts to a
 * company that was rolled back. In-process on purpose: there is no message broker in
 * this system yet, and an outbox table would be infrastructure with no consumer.
 * Introducing one later means changing the listener, not the publisher.
 *
 * <p>Sealed so the compiler can enforce exhaustive handling: a new event type will not
 * be silently ignored by an existing {@code switch}.
 *
 * <p>Events carry identifiers and the minimum context a listener needs — never the
 * whole entity, which would be stale by the time it is read.
 *
 * <p><b>Two ids, on purpose.</b> {@code id} is the {@code companies} row key, the one
 * every {@code /api/v1/companies/{id}} URL carries. {@code companyId} is the ownership
 * key stamped on every row the company owns and carried in the JWT. They are separate
 * values today only because collapsing them needs a data migration; see
 * {@code MEMORY/adr/ADR-001.md}.
 */
public sealed interface CompanyEvent {

    /** The {@code companies} row key. */
    UUID id();

    /** The ownership key stamped on every row this company owns. */
    UUID companyId();

    String companyCode();

    Instant occurredAt();

    /** A company and its entire default configuration now exist. */
    record CompanyCreated(UUID id,
                          UUID companyId,
                          String companyCode,
                          UUID subscriptionPlanId,
                          UUID adminUserId,
                          CompanyStatus status,
                          Instant occurredAt) implements CompanyEvent {
    }

    /**
     * @param changedFields field name to {@code {from, to}}, the same shape the audit
     *                      trail records — only fields that actually changed
     */
    record CompanyUpdated(UUID id,
                          UUID companyId,
                          String companyCode,
                          Map<String, Object> changedFields,
                          Instant occurredAt) implements CompanyEvent {
    }

    record CompanyActivated(UUID id,
                            UUID companyId,
                            String companyCode,
                            CompanyStatus previousStatus,
                            Instant occurredAt) implements CompanyEvent {
    }

    /** @param reason required by the API, and the first thing support will ask for */
    record CompanySuspended(UUID id,
                            UUID companyId,
                            String companyCode,
                            CompanyStatus previousStatus,
                            String reason,
                            Instant occurredAt) implements CompanyEvent {
    }

    /** The company is now {@code INACTIVE} — reversible, and distinct from suspension. */
    record CompanyDeactivated(UUID id,
                              UUID companyId,
                              String companyCode,
                              CompanyStatus previousStatus,
                              String reason,
                              Instant occurredAt) implements CompanyEvent {
    }

    record CompanyExpired(UUID id,
                          UUID companyId,
                          String companyCode,
                          CompanyStatus previousStatus,
                          Instant occurredAt) implements CompanyEvent {
    }

    /**
     * The company's subscription changed — assigned to a plan, renewed, or suspended.
     *
     * @param action    {@code ASSIGNED}, {@code RENEWED} or {@code SUSPENDED}
     * @param planCode  the plan in force after the change
     */
    record SubscriptionChanged(UUID id,
                               UUID companyId,
                               String companyCode,
                               String action,
                               UUID subscriptionPlanId,
                               String planCode,
                               java.time.LocalDate subscriptionStartDate,
                               java.time.LocalDate subscriptionEndDate,
                               Instant occurredAt) implements CompanyEvent {
    }
}
