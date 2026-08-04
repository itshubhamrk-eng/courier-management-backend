package com.courier.modules.company.domain;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for a company search. Every field is optional; null means
 * "do not constrain on this".
 *
 * <p>In {@code domain} so the controller (which builds it from the search request) and
 * the service (which hands it to {@link CompanySpecifications}) share it without either
 * depending on the other's package.
 *
 * @param statuses            match any of these statuses
 * @param active              the denormalised operational flag
 * @param subscriptionPlanId  companies on one plan
 * @param country             exact, case-insensitive
 * @param state               exact, case-insensitive
 * @param city                exact, case-insensitive
 * @param expiringBefore      subscription or trial ends on or before this date
 * @param createdFrom         created on or after this date
 * @param createdTo           created on or before this date
 * @param search              free text over code, name, legal name, email and mobile
 */
public record CompanyCriteria(
        Set<CompanyStatus> statuses,
        Boolean active,
        UUID subscriptionPlanId,
        String country,
        String state,
        String city,
        LocalDate expiringBefore,
        LocalDate createdFrom,
        LocalDate createdTo,
        String search
) {

    public static CompanyCriteria none() {
        return new CompanyCriteria(null, null, null, null, null, null, null, null, null, null);
    }
}
