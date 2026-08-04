package com.courier.modules.customer.application.command;

import com.courier.modules.customer.domain.AddressType;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCustomerAddressCommand(
        AddressType addressType,
        UUID countryId,
        UUID stateId,
        UUID districtId,
        UUID cityId,
        UUID areaId,
        UUID pincodeId,
        String addressLine1,
        String addressLine2,
        String landmark,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isDefaultPickup,
        Boolean isDefaultDelivery
) {
}
