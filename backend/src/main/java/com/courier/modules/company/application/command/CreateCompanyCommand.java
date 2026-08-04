package com.courier.modules.company.application.command;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to {@code CompanyService.create}.
 *
 * <p>The service takes this rather than the REST DTO so {@code application} does not
 * depend on {@code api}; the mapper in the {@code api} layer converts.
 *
 * <p>{@code companyId} is absent by design — it is generated, never supplied. So is
 * {@code status}: it follows from the plan's trial period.
 *
 * <p>The {@code admin*} fields describe the first user created with the company. When
 * {@code adminEmail} is null the company's own {@code email} is used, which is the
 * common case.
 */
public record CreateCompanyCommand(
        String companyCode,
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
        LocalDate subscriptionStartDate,
        String adminEmail,
        String adminFirstName,
        String adminLastName,
        String adminMobile
) {
}
