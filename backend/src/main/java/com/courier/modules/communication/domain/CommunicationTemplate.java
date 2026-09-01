package com.courier.modules.communication.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * One customizable message per (company, event, channel) — e.g. "SHIPMENT_BOOKED +
 * WHATSAPP". {@code content} carries {@code {{variable}}} placeholders, substituted by
 * {@code TemplateRenderer} at send time — see it for the supported variable list.
 *
 * <p>{@link #status} is the actual "Company Admin can enable/disable each channel per
 * event" switch the brief describes — deliberately not {@code CommunicationSetting.enabled},
 * which is a coarser per-channel master switch only ("is WhatsApp usable for this company at
 * all"). An {@code INACTIVE} template means this one event never sends on this one channel,
 * independent of the channel's own master switch.
 *
 * <p>Company-owned, seeded lazily the first time a company's templates are read (or the
 * dispatch listener needs one) — see {@code CommunicationTemplateServiceImpl
 * .getOrSeedDefaults} — the same "get-or-create" precedent {@code CompanySettings} already
 * set, not a migration-time INSERT across every existing company.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "communication_template",
        uniqueConstraints = @UniqueConstraint(name = "uk_communication_template_company_event_channel",
                columnNames = {"company_id", "event_type", "channel"}))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class CommunicationTemplate extends CompanyOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private CommunicationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private CommunicationChannel channel;

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    /** Email only; null for WhatsApp/SMS. */
    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TemplateStatus status = TemplateStatus.ACTIVE;

    public boolean isActive() {
        return status == TemplateStatus.ACTIVE;
    }
}
