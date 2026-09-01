package com.courier.modules.communication.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * One row per (company, channel) — the channel-level master switch and provider config.
 * {@link #enabled} here means "usable at all for this company"; whether one specific event
 * fires on this channel is {@code CommunicationTemplate.status} instead — see that class's
 * own doc for why the two are deliberately separate switches.
 *
 * <p>{@link #secretEncrypted} is the one real secret a channel needs (WhatsApp access
 * token / SMS API key) — AES-256-GCM via {@link EncryptedStringConverter}, the same
 * converter {@code CompanyRazorpayConfig} (V46) already uses for its own key secret. Never
 * returned by the API, never logged — see {@code CommunicationSettingResponse}. Email has
 * no per-company secret: platform SMTP credentials are environment-configured
 * ({@code spring.mail.*}), a company only sets its own from-name/from-email identity in
 * {@link #configJson}.
 *
 * <p>{@link #configJson} holds the non-secret provider config: WhatsApp
 * {@code {phoneNumberId, businessAccountId}}, SMS {@code {apiUrl, senderId}}, Email
 * {@code {fromName, fromEmail}}. Plain JSON text, not a real jsonb column — this project's
 * MySQL/Hibernate setup has no existing JSON-column precedent to follow, and the shape
 * genuinely differs per channel, so a fixed set of typed columns would leave two of the
 * three channels' columns always null.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "communication_setting",
        uniqueConstraints = @UniqueConstraint(name = "uk_communication_setting_company_channel",
                columnNames = {"company_id", "channel"}))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class CommunicationSetting extends CompanyOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private CommunicationChannel channel;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    /** Plaintext in memory once loaded — {@link EncryptedStringConverter} only affects the
     *  {@code secret_encrypted} column's own on-disk representation, the same "field name
     *  vs. column name" split {@code CompanyRazorpayConfig.keySecret} already uses. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret_encrypted", length = 2000)
    private String secret;

    public boolean hasSecret() {
        return secret != null && !secret.isBlank();
    }
}
