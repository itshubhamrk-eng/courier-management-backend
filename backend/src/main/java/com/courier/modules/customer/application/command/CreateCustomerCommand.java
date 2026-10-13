package com.courier.modules.customer.application.command;

import com.courier.modules.customer.domain.BillingFrequency;
import com.courier.modules.customer.domain.CustomerPaymentMode;
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
        String panNumber,
        /** Null means "default true" — see {@code CustomerServiceImpl.create}. */
        Boolean whatsappEnabled,
        Boolean smsEnabled,
        Boolean emailEnabled,
        String contactPerson,
        CustomerPaymentMode defaultPaymentMode,
        BillingFrequency billingFrequency,
        String billingAddressLine1,
        String billingAddressLine2,
        String billingCity,
        String billingState,
        String billingPincode
) {
}
