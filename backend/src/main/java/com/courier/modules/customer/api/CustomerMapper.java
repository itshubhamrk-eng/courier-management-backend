package com.courier.modules.customer.api;

import com.courier.modules.customer.api.dto.CreateCustomerAddressRequest;
import com.courier.modules.customer.api.dto.CreateCustomerRequest;
import com.courier.modules.customer.api.dto.CustomerAddressResponse;
import com.courier.modules.customer.api.dto.CustomerResponse;
import com.courier.modules.customer.api.dto.CustomerSearchRequest;
import com.courier.modules.customer.api.dto.CustomerSummaryResponse;
import com.courier.modules.customer.api.dto.UpdateCustomerAddressRequest;
import com.courier.modules.customer.api.dto.UpdateCustomerRequest;
import com.courier.modules.customer.application.command.CreateCustomerAddressCommand;
import com.courier.modules.customer.application.command.CreateCustomerCommand;
import com.courier.modules.customer.application.command.UpdateCustomerAddressCommand;
import com.courier.modules.customer.application.command.UpdateCustomerCommand;
import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customer.domain.CustomerAddress;
import com.courier.modules.customer.domain.CustomerCriteria;
import org.springframework.stereotype.Component;

import java.util.List;

/** Wire contract <-> application/domain types for customers and their addresses. */
@Component
public class CustomerMapper {

    public CreateCustomerCommand toCommand(CreateCustomerRequest r) {
        return new CreateCustomerCommand(
                r.customerCode(), r.customerType(), r.companyName(),
                r.firstName(), r.middleName(), r.lastName(),
                r.mobile(), r.alternateMobile(), r.email(),
                r.gstNumber(), r.panNumber(),
                r.whatsappEnabled(), r.smsEnabled(), r.emailEnabled());
    }

    public UpdateCustomerCommand toCommand(UpdateCustomerRequest r) {
        return new UpdateCustomerCommand(
                r.customerType(), r.companyName(),
                r.firstName(), r.middleName(), r.lastName(),
                r.mobile(), r.alternateMobile(), r.email(),
                r.gstNumber(), r.panNumber(),
                r.whatsappEnabled(), r.smsEnabled(), r.emailEnabled(), r.version());
    }

    public CustomerCriteria toCriteria(CustomerSearchRequest r) {
        CustomerSearchRequest safe = r == null ? CustomerSearchRequest.empty() : r;
        return new CustomerCriteria(safe.customerType(), safe.status(), safe.search());
    }

    public CreateCustomerAddressCommand toCommand(CreateCustomerAddressRequest r) {
        return new CreateCustomerAddressCommand(
                r.addressType(), r.countryId(), r.stateId(), r.districtId(), r.cityId(),
                r.areaId(), r.pincodeId(), r.addressLine1(), r.addressLine2(), r.landmark(),
                r.latitude(), r.longitude(), r.isDefaultPickup(), r.isDefaultDelivery());
    }

    public UpdateCustomerAddressCommand toCommand(UpdateCustomerAddressRequest r) {
        return new UpdateCustomerAddressCommand(
                r.addressType(), r.countryId(), r.stateId(), r.districtId(), r.cityId(),
                r.areaId(), r.pincodeId(), r.addressLine1(), r.addressLine2(), r.landmark(),
                r.latitude(), r.longitude(), r.isDefaultPickup(), r.isDefaultDelivery(), r.version());
    }

    public CustomerResponse toResponse(Customer c, List<CustomerAddress> addresses) {
        return new CustomerResponse(
                c.getId(), c.getCompanyId(), c.getCustomerCode(), c.getCustomerType(),
                c.getCompanyName(), c.getFirstName(), c.getMiddleName(), c.getLastName(),
                c.displayName(),
                c.getMobile(), c.getAlternateMobile(), c.getEmail(),
                c.getGstNumber(), c.getPanNumber(),
                c.isWhatsappEnabled(), c.isSmsEnabled(), c.isEmailEnabled(), c.getStatus(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt(), c.getVersion(),
                addresses.stream().map(this::toResponse).toList());
    }

    public CustomerSummaryResponse toSummary(Customer c) {
        return new CustomerSummaryResponse(
                c.getId(), c.getCustomerCode(), c.getCustomerType(), c.displayName(),
                c.getMobile(), c.getEmail(), c.getStatus(), c.getCreatedAt(), c.getVersion());
    }

    public CustomerAddressResponse toResponse(CustomerAddress a) {
        return new CustomerAddressResponse(
                a.getId(), a.getCompanyId(), a.getCustomerId(), a.getAddressType(),
                a.getCountryId(), a.getStateId(), a.getDistrictId(), a.getCityId(), a.getAreaId(), a.getPincodeId(),
                a.getAddressLine1(), a.getAddressLine2(), a.getLandmark(),
                a.getLatitude(), a.getLongitude(),
                a.isDefaultPickup(), a.isDefaultDelivery(), a.getStatus(),
                a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedBy(), a.getUpdatedAt(), a.getVersion());
    }
}
