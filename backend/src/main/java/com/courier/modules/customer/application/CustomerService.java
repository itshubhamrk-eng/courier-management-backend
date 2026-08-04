package com.courier.modules.customer.application;

import com.courier.modules.customer.application.command.CreateCustomerCommand;
import com.courier.modules.customer.application.command.UpdateCustomerCommand;
import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customer.domain.CustomerCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Use cases for customers — reusable master data, independent of Shipment Order.
 *
 * <p><b>Audiences:</b>
 * <ul>
 *   <li>{@code COMPANY_ADMIN} — full management.</li>
 *   <li>{@code BRANCH_MANAGER} / {@code OPERATOR} — create and update while booking a
 *       shipment at the counter; neither deletes, activates or deactivates a customer.</li>
 *   <li>Any other authenticated user of the company — read only.</li>
 * </ul>
 * A {@code SUPER_ADMIN} never reaches a customer at all — it is a company's own
 * operational record (see the invariant in {@code MEMORY/AI_CONTEXT.md}).
 */
public interface CustomerService {

    Customer create(CreateCustomerCommand command);

    Customer update(UUID id, UpdateCustomerCommand command);

    Customer getById(UUID id);

    Page<Customer> search(CustomerCriteria criteria, Pageable pageable);

    Customer activate(UUID id);

    Customer deactivate(UUID id);
}
