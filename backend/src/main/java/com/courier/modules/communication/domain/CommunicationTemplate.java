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

    /** UTILITY, MARKETING or AUTHENTICATION — what the template is submitted to Meta as. */
    @Column(name = "category", nullable = false, length = 20)
    @Builder.Default
    private String category = "UTILITY";

    /** Meta language code (en_US, en, hi, mr). The send uses this, so it must be the language the
     *  template was approved in. */
    @Column(name = "language", nullable = false, length = 10)
    @Builder.Default
    private String language = "en_US";

    @Column(name = "header_text", length = 60)
    private String headerText;

    @Column(name = "footer_text", length = 60)
    private String footerText;

    /** JSON list of {@link TemplateButton}; see {@code TemplateComponentsCodec}. */
    @Column(name = "buttons_json", columnDefinition = "TEXT")
    private String buttonsJson;

    /** JSON {variable: sample} overriding the built-in example values. */
    @Column(name = "variable_samples_json", columnDefinition = "TEXT")
    private String variableSamplesJson;

    /** Meta's own template id — set once the app has submitted this template. */
    @Column(name = "meta_template_id", length = 40)
    private String metaTemplateId;

    /** PENDING / APPROVED / REJECTED / PAUSED / DISABLED, as last reported by Meta; null when the
     *  app never submitted this template (it may still exist in Meta, made by hand). */
    @Column(name = "meta_status", length = 20)
    private String metaStatus;

    /** Meta's rejection reason, or a note from the app (e.g. the log-only provider). */
    @Column(name = "meta_status_detail", length = 500)
    private String metaStatusDetail;

    @Column(name = "meta_category", length = 20)
    private String metaCategory;

    @Column(name = "meta_language", length = 10)
    private String metaLanguage;

    @Column(name = "meta_submitted_at")
    private java.time.Instant metaSubmittedAt;

    public boolean isActive() {
        return status == TemplateStatus.ACTIVE;
    }

    public boolean hasMetaSubmission() {
        return metaStatus != null;
    }

    /** False only when the app *knows* Meta has not approved this template. */
    public boolean isApprovedOrUnknownAtMeta() {
        return metaStatus == null || "APPROVED".equals(metaStatus);
    }

    public void clearMetaSubmission() {
        metaTemplateId = null;
        metaStatus = null;
        metaStatusDetail = null;
        metaCategory = null;
        metaLanguage = null;
        metaSubmittedAt = null;
    }
}
