package com.courier.modules.company.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to {@code CompanyService.update}. A full replacement (PUT), not a patch.
 *
 * <p>Four fields are deliberately absent because they are immutable or have their own
 * endpoint:
 * <ul>
 *   <li>{@code companyCode} — referenced by operational data; changing it rewrites history.</li>
 *   <li>{@code companyId} — every company-owned row points at it.</li>
 *   <li>{@code status} / {@code isActive} — moved only through activate, suspend and
 *       expire, so each transition is separately audited and validated.</li>
 * </ul>
 *
 * @param expectedVersion the version the client last read; a stale value is rejected
 *                        with {@code 409 CONCURRENT_MODIFICATION}
 */
public record UpdateCompanyCommand(
        String companyName,
        String legalName,
        String displayName,
        UUID subscriptionPlanId,
        String email,
        String mobile,
        String alternateMobile,
        String website,
        String gstNumber,
        String panNumber,
        String cinNumber,
        String logo,
        String favicon,
        String addressLine1,
        String addressLine2,
        String country,
        String state,
        String city,
        String postalCode,
        String timezone,
        String currency,
        String language,
        String dateFormat,
        String timeFormat,
        String remarks,
        LocalDate trialEndDate,
        LocalDate subscriptionStartDate,
        LocalDate subscriptionEndDate,
        Long expectedVersion
) {
}
