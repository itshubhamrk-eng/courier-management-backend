package com.courier.modules.finance.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * A company's own Razorpay account, used instead of the platform-wide gateway
 * ({@code RazorpayProperties}/env vars) when {@link #enabled} and {@link #hasCredentials()}
 * — both a key id and secret saved for the currently selected {@link #mode}. See
 * {@code CompanyPaymentGatewayResolver}.
 *
 * <p>Test and live are separate, independently-saved credential pairs (V82) — a company can
 * hold both at once and flip {@link #mode} between them without re-entering either. {@link
 * #getKeyId()}/{@link #getKeySecret()} resolve to whichever pair {@link #mode} currently
 * selects, which is what {@code CompanyPaymentGatewayResolver} and {@code
 * CompanyRazorpayConfigServiceImplTest} actually use.
 *
 * <p>One row per company, created lazily on first save — unlike {@code CompanySettings},
 * most companies never touch this and don't need an empty row seeded for them. Soft delete
 * does not apply — same reasoning as {@code CompanySettings} — so there is no
 * {@code @SQLRestriction} and no soft-delete method is ever called on this entity.
 *
 * <p>Both secrets are encrypted at rest ({@link EncryptedStringConverter}) and are never
 * returned by the API — only each key id (publishable) and whether each secret is
 * configured are ever exposed. See {@code CompanyRazorpayConfigResponse}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "company_razorpay_config",
        uniqueConstraints = @UniqueConstraint(name = "uk_company_razorpay_config_company",
                columnNames = "company_id"))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
public class CompanyRazorpayConfig extends CompanyOwnedEntity {

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    /** Which credential pair below is actually used for wallet recharge right now. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private RazorpayMode mode = RazorpayMode.TEST;

    /** Publishable test key id, handed to the browser checkout when {@link #mode} is TEST. */
    @Column(name = "test_key_id", length = 255)
    private String testKeyId;

    /** Test signing secret. Encrypted at rest; never logged, never returned to a client. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "test_key_secret_encrypted", length = 1000)
    private String testKeySecret;

    /** Publishable live key id, handed to the browser checkout when {@link #mode} is LIVE. */
    @Column(name = "live_key_id", length = 255)
    private String liveKeyId;

    /** Live signing secret. Encrypted at rest; never logged, never returned to a client. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "live_key_secret_encrypted", length = 1000)
    private String liveKeySecret;

    /** The key id of whichever mode is currently selected. */
    public String getKeyId() {
        return mode == RazorpayMode.LIVE ? liveKeyId : testKeyId;
    }

    /** The key secret of whichever mode is currently selected. */
    public String getKeySecret() {
        return mode == RazorpayMode.LIVE ? liveKeySecret : testKeySecret;
    }

    /** Whether this row is actually usable as a gateway — the resolver's own test. */
    public boolean hasCredentials() {
        String keyId = getKeyId();
        String keySecret = getKeySecret();
        return enabled
                && keyId != null && !keyId.isBlank()
                && keySecret != null && !keySecret.isBlank();
    }
}
