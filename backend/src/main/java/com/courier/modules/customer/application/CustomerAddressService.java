package com.courier.modules.customer.application;

import com.courier.modules.customer.application.command.CreateCustomerAddressCommand;
import com.courier.modules.customer.application.command.UpdateCustomerAddressCommand;
import com.courier.modules.customer.domain.CustomerAddress;

import java.util.List;
import java.util.UUID;

/**
 * Use cases for a customer's addresses. Same audiences as {@link CustomerService},
 * except address delete is {@code COMPANY_ADMIN} only — no seeded role starts with
 * {@code ADDRESS_DELETE} (see {@code DefaultRoleCatalog}), so a counter clerk who mistypes
 * a pincode corrects the address with an update rather than removing it.
 */
public interface CustomerAddressService {

    CustomerAddress create(UUID customerId, CreateCustomerAddressCommand command);

    CustomerAddress update(UUID customerId, UUID addressId, UpdateCustomerAddressCommand command);

    /** Soft delete. */
    void delete(UUID customerId, UUID addressId);

    List<CustomerAddress> listByCustomer(UUID customerId);
}
