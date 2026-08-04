package com.courier.modules.customer.domain;

import java.util.Set;

/**
 * Filter criteria for a customer search. Every field optional; null means "do not
 * constrain". Lives in {@code domain} so controller and service share it.
 *
 * @param customerTypes match any of these types
 * @param statuses      match any of these statuses
 * @param search        free text over code, name, company name, mobile and email
 */
public record CustomerCriteria(
        Set<CustomerType> customerTypes,
        Set<CustomerStatus> statuses,
        String search
) {

    public static CustomerCriteria none() {
        return new CustomerCriteria(null, null, null);
    }
}
