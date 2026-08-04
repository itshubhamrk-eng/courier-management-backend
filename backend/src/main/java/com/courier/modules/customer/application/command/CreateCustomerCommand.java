package com.courier.modules.customer.application.command;

import com.courier.modules.customer.domain.CustomerType;

/**
 * @param customerCode blank/null means "generate one" — see {@code CustomerCodeGenerator}
 */
public record CreateCustomerCommand(
        String customerCode,
        CustomerType customerType,
        String companyName,
        String firstName,
        String middleName,
        String lastName,
        String mobile,
        String alternateMobile,
        String email,
        String gstNumber,
        String panNumber
) {
}
