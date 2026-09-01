package com.courier.modules.communication.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (shipment, event, channel) ever attempted — the retry ledger and the
 * duplicate-protection record in one. A retry updates {@link #attemptCount}/{@link #status}
 * on this SAME row rather than inserting a new one: that is what "do not send a duplicate
 * notification for the same shipment/event/channel unless explicitly retried" means in
 * practice here, and it's enforced physically by the table's own unique key, not just by
 * application discipline.
 *
 * <p>{@link #status} progression: {@code PENDING} (queued by
 * {@code ShipmentCommunicationListener}) -&gt; {@code SENT} (provider accepted it) or
 * {@code FAILED} (provider call failed; {@code CommunicationDispatchJob} retries it while
 * {@link #attemptCount} is under the configured cap and {@link #nextRetryAt} has passed) or
 * {@code CANCELLED} (never attempted — channel disabled, no active template). {@code
 * DELIVERED} is modelled but never reached in this dev environment — no provider
 * delivery-receipt webhook exists yet for any channel; see {@code CommunicationStatus}'s
 * own doc.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "communication_log",
        uniqueConstraints = @UniqueConstraint(name = "uk_communication_log_shipment_event_channel",
                columnNames = {"shipment_id", "event_type", "channel"}),
        indexes = {
                @Index(name = "ix_communication_log_company_status", columnList = "company_id, status"),
                @Index(name = "ix_communication_log_next_retry", columnList = "status, next_retry_at")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class CommunicationLog extends CompanyOwnedEntity {

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private CommunicationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private CommunicationChannel channel;

    @Column(name = "recipient", nullable = false, length = 150)
    private String recipient;

    @Column(name = "template_id")
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CommunicationStatus status = CommunicationStatus.PENDING;

    @Column(name = "provider_message_id", length = 150)
    private String providerMessageId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public boolean isTerminal() {
        return status == CommunicationStatus.SENT || status == CommunicationStatus.DELIVERED
                || status == CommunicationStatus.CANCELLED;
    }
}
