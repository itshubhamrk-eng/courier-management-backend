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
import java.time.LocalDate;

/**
 * One daily business summary to one phone number for one business day. The unique key
 * {@code (company_id, report_date, recipient)} is the duplicate guard: the job ticks every few
 * minutes after 09:00 so a missed morning self-heals, and this row is what makes a later tick
 * (or a restart) see "already sent" instead of sending again.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "daily_report_log",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_report_log_company_day_recipient",
                columnNames = {"company_id", "report_date", "recipient"}),
        indexes = @Index(name = "ix_daily_report_log_company_date", columnList = "company_id, report_date"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class DailyReportLog extends CompanyOwnedEntity {

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "recipient", nullable = false, length = 30)
    private String recipient;

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

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    /** SENT or, once Meta's receipt arrives, DELIVERED — either way it must never be sent again. */
    public boolean isSent() {
        return status == CommunicationStatus.SENT || status == CommunicationStatus.DELIVERED;
    }
}
