package com.courier.modules.customer.api.dto;

import com.courier.modules.customer.domain.AddressType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Body of {@code POST /api/v1/customers/{id}/addresses}. */
@Schema(name = "CreateCustomerAddressRequest", description = "New address for a customer")
public record CreateCustomerAddressRequest(

        @NotNull AddressType addressType,

        UUID countryId, UUID stateId, UUID districtId, UUID cityId, UUID areaId, UUID pincodeId,

        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 150) String landmark,

        @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 6) BigDecimal longitude,

        @Schema(description = "At most one address per customer may be the default pickup; "
                + "setting this clears the flag on any other")
        Boolean isDefaultPickup,
        @Schema(description = "At most one address per customer may be the default delivery")
        Boolean isDefaultDelivery
) {
}
