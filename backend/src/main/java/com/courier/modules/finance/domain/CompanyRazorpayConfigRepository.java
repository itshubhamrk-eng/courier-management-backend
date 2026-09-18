package com.courier.modules.finance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * At most one row per company. Company-owned: the Hibernate filter narrows this to the
 * caller's own company on every query.
 */
public interface CompanyRazorpayConfigRepository extends JpaRepository<CompanyRazorpayConfig, UUID> {

    Optional<CompanyRazorpayConfig> findByCompanyId(UUID companyId);

    /**
     * Recovery path for a row whose encrypted column(s) no longer decrypt under the
     * running {@code SECRETS_ENCRYPTION_KEY} (rotated after the row was saved). A plain
     * native statement — never loads the entity, so it never invokes
     * {@code EncryptedStringConverter} and never re-throws the failure it's meant to
     * clear. See {@code CompanyRazorpayConfigServiceImpl.get}/{@code .update}.
     */
    @Modifying
    @Query(value = "update company_razorpay_config set test_key_secret_encrypted = null, "
            + "live_key_secret_encrypted = null where company_id = :companyId", nativeQuery = true)
    void clearUnreadableSecrets(@Param("companyId") UUID companyId);
}
