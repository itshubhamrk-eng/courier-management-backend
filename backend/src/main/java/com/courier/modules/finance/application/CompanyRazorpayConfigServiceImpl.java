package com.courier.modules.finance.application;

import com.courier.modules.finance.application.command.CompanyRazorpayConfigCommand;
import com.courier.modules.finance.domain.CompanyRazorpayConfig;
import com.courier.modules.finance.domain.CompanyRazorpayConfigRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * A company's own Razorpay credentials.
 *
 * <p>Both read and write are {@code COMPANY_ADMIN} only — tighter than
 * {@code CompanySettingsService}'s "any authenticated user reads", because even a masked
 * view of payment credentials (key id, whether a secret is set) is more sensitive than the
 * rest of company settings.
 *
 * <p>Unlike {@code CompanySettings}, this row is not seeded on first read: most companies
 * never configure their own gateway, and an empty row per company would be pure clutter.
 * {@link #get()} returns a transient, unpersisted default instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyRazorpayConfigServiceImpl implements CompanyRazorpayConfigService {

    private static final String ENTITY = "CompanyRazorpayConfig";
    private static final String COMPANY_ADMIN_ONLY = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final CompanyRazorpayConfigRepository repository;
    private final CompanyRazorpayConfigRepairService repairService;
    private final AuditService auditService;

    /**
     * A stored secret that no longer decrypts under the running
     * {@code SECRETS_ENCRYPTION_KEY} (rotated after the row was saved, or the row was
     * written under a different key) must not 500 every read of company settings — this is
     * a masked read, not a use of the secret. Treated the same as "never configured", and
     * the unreadable secret(s) are cleared in the DB (see {@link CompanyRazorpayConfigRepairService})
     * so a later {@link #update} doesn't hit the same failure trying to load this row.
     * Logged loudly since it hides a real data problem otherwise.
     */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public CompanyRazorpayConfig get() {
        UUID companyId = requireCompany();
        try {
            return repository.findByCompanyId(companyId).orElseGet(CompanyRazorpayConfig::new);
        } catch (DataAccessException | IllegalStateException e) {
            log.warn("Razorpay config for company {} could not be decrypted — treating as "
                    + "not configured and clearing the unreadable secret(s). The stored "
                    + "ciphertext no longer matches SECRETS_ENCRYPTION_KEY; the company "
                    + "admin must re-enter it.", companyId, e);
            repairService.clearUnreadableSecrets(companyId);
            return new CompanyRazorpayConfig();
        }
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public CompanyRazorpayConfig update(CompanyRazorpayConfigCommand command) {
        UUID companyId = requireCompany();
        CompanyRazorpayConfig config;
        try {
            config = repository.findByCompanyId(companyId).orElseGet(CompanyRazorpayConfig::new);
        } catch (DataAccessException | IllegalStateException e) {
            log.warn("Razorpay config for company {} could not be decrypted while updating — "
                    + "clearing the unreadable secret(s) in a separate transaction. The "
                    + "Hibernate session that just failed cannot be reused, so this attempt "
                    + "stops here; the next save (now against a clean row) will succeed.",
                    companyId, e);
            repairService.clearUnreadableSecrets(companyId);
            throw new BusinessRuleException(
                    "The previously stored Razorpay secret could not be read (it no longer "
                            + "matches the server's current encryption key) and has been "
                            + "cleared. Please enter the key id/secret again and save.");
        }

        String testKeyId = command.testKeyId() == null ? null : command.testKeyId().trim();
        String liveKeyId = command.liveKeyId() == null ? null : command.liveKeyId().trim();

        config.setTestKeyId(testKeyId);
        if (command.hasNewTestSecret()) {
            config.setTestKeySecret(command.testKeySecret().trim());
        }
        config.setLiveKeyId(liveKeyId);
        if (command.hasNewLiveSecret()) {
            config.setLiveKeySecret(command.liveKeySecret().trim());
        }
        config.setMode(command.mode());

        if (command.enabled()) {
            String activeKeyId = config.getKeyId();
            if (activeKeyId == null || activeKeyId.isBlank()) {
                throw new BusinessRuleException(
                        "A " + command.mode().name().toLowerCase() + " key id is required to enable Razorpay.");
            }
            String activeSecret = config.getKeySecret();
            if (activeSecret == null || activeSecret.isBlank()) {
                throw new BusinessRuleException(
                        "A " + command.mode().name().toLowerCase() + " key secret is required to enable Razorpay.");
            }
        }
        config.setEnabled(command.enabled());

        CompanyRazorpayConfig saved = repository.save(config);

        auditService.record(AuditAction.COMPANY_RAZORPAY_CONFIG_UPDATED, ENTITY, saved.getId(),
                Map.of("enabled", saved.isEnabled(),
                        "mode", saved.getMode().name(),
                        "testKeyId", saved.getTestKeyId() == null ? "" : saved.getTestKeyId(),
                        "liveKeyId", saved.getLiveKeyId() == null ? "" : saved.getLiveKeyId(),
                        "testSecretRotated", command.hasNewTestSecret(),
                        "liveSecretRotated", command.hasNewLiveSecret()));

        return saved;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Razorpay credentials belong to a "
                        + "company, so this must be performed by a user of that company."));
    }
}
