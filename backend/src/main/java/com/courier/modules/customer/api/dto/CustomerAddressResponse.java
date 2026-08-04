package com.courier.modules.customer.api.dto;

import com.courier.modules.customer.domain.AddressType;
import com.courier.modules.customer.domain.CustomerStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "CustomerAddressResponse", description = "Customer address in full")
public record CustomerAddressResponse(
        UUID id, UUID companyId, UUID customerId, AddressType addressType,
        UUID countryId, UUID stateId, UUID districtId, UUID cityId, UUID areaId, UUID pincodeId,
        String addressLine1, String addressLine2, String landmark,
        BigDecimal latitude, BigDecimal longitude,
        boolean isDefaultPickup, boolean isDefaultDelivery,
        CustomerStatus status,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
