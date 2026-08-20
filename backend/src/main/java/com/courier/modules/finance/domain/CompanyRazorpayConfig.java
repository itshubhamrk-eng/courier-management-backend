package com.courier.modules.finance.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * A company's own Razorpay account, used instead of the platform-wide gateway
 * ({@code RazorpayProperties}/env vars) when {@link #enabled} and both {@link #keyId} and
 * {@link #keySecret} are set. See {@code CompanyPaymentGatewayResolver}.
 *
 * <p>One row per company, created lazily on first save — unlike {@code CompanySettings},
 * most companies never touch this and don't need an empty row seeded for them. Soft delete
 * does not apply — same reasoning as {@code CompanySettings} — so there is no
 * {@code @SQLRestriction} and no soft-delete method is ever called on this entity.
 *
 * <p>{@link #keySecret} is encrypted at rest ({@link EncryptedStringConverter}) and is
 * never returned by the API — only {@link #keyId} (publishable) and whether a secret is
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

    /** Publishable key id, handed to the browser checkout. */
    @Column(name = "key_id", length = 255)
    private String keyId;

    /** Signing secret. Encrypted at rest; never logged, never returned to a client. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "key_secret_encrypted", length = 1000)
    private String keySecret;

    /** Whether this row is actually usable as a gateway — the resolver's own test. */
    public boolean hasCredentials() {
        return enabled
                && keyId != null && !keyId.isBlank()
                && keySecret != null && !keySecret.isBlank();
    }
}
