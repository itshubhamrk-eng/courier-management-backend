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
@Service
@RequiredArgsConstructor
public class CompanyRazorpayConfigServiceImpl implements CompanyRazorpayConfigService {

    private static final String ENTITY = "CompanyRazorpayConfig";
    private static final String COMPANY_ADMIN_ONLY = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final CompanyRazorpayConfigRepository repository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public CompanyRazorpayConfig get() {
        return repository.findByCompanyId(requireCompany()).orElseGet(CompanyRazorpayConfig::new);
    }

    @Override
    @Transactional
    @PreAuthorize(COMPANY_ADMIN_ONLY)
    public CompanyRazorpayConfig update(CompanyRazorpayConfigCommand command) {
        UUID companyId = requireCompany();
        CompanyRazorpayConfig config = repository.findByCompanyId(companyId)
                .orElseGet(CompanyRazorpayConfig::new);

        String keyId = command.keyId() == null ? null : command.keyId().trim();

        if (command.enabled()) {
            if (keyId == null || keyId.isBlank()) {
                throw new BusinessRuleException("A key id is required to enable Razorpay.");
            }
            boolean hasSecret = command.hasNewSecret()
                    || (config.getKeySecret() != null && !config.getKeySecret().isBlank());
            if (!hasSecret) {
                throw new BusinessRuleException("A key secret is required to enable Razorpay.");
            }
        }

        config.setEnabled(command.enabled());
        config.setKeyId(keyId);
        if (command.hasNewSecret()) {
            config.setKeySecret(command.keySecret().trim());
        }

        CompanyRazorpayConfig saved = repository.save(config);

        auditService.record(AuditAction.COMPANY_RAZORPAY_CONFIG_UPDATED, ENTITY, saved.getId(),
                Map.of("enabled", saved.isEnabled(),
                        "keyId", saved.getKeyId() == null ? "" : saved.getKeyId(),
                        "secretRotated", command.hasNewSecret()));

        return saved;
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Razorpay credentials belong to a "
                        + "company, so this must be performed by a user of that company."));
    }
}
