package com.courier.modules.company.application;

import com.courier.modules.company.application.command.CompanySettingsCommand;
import com.courier.modules.company.domain.CompanySettings;

/**
 * A company's configurable behaviour settings.
 *
 * <p>Reads are open to any authenticated user of the company — many roles and modules need
 * to know, say, the weight unit or whether COD is on. Writes are {@code COMPANY_ADMIN}
 * only. Enforced with {@code @PreAuthorize} on {@link CompanySettingsServiceImpl}.
 *
 * <p>There is exactly one row per company, created on first access. Every method operates
 * on the caller's own company; there is no cross-company write.
 */
public interface CompanySettingsService {

    /** The caller's settings, creating the row with defaults if it does not exist yet. */
    CompanySettings get();

    /** Full update across all sections. Merges supplied fields; rejects a stale version. */
    CompanySettings replace(CompanySettingsCommand command);

    CompanySettings patchGeneral(CompanySettingsCommand command);

    CompanySettings patchShipment(CompanySettingsCommand command);

    CompanySettings patchFinance(CompanySettingsCommand command);

    CompanySettings patchSecurity(CompanySettingsCommand command);

    CompanySettings patchNotification(CompanySettingsCommand command);

    CompanySettings patchBranding(CompanySettingsCommand command);
}
