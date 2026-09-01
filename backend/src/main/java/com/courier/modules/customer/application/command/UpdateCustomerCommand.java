package com.courier.modules.customer.application.command;

import com.courier.modules.customer.domain.CustomerType;

/** Full replacement of the editable fields. {@code customerCode} is immutable. */
public record UpdateCustomerCommand(
        CustomerType customerType,
        String companyName,
        String firstName,
        String middleName,
        String lastName,
        String mobile,
        String alternateMobile,
        String email,
        String gstNumber,
        String panNumber,
        boolean whatsappEnabled,
        boolean smsEnabled,
        boolean emailEnabled,
        Long expectedVersion
) {
}
