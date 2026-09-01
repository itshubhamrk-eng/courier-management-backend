package com.courier.modules.customer.api.dto;

import com.courier.modules.customer.domain.CustomerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/customers/{id}}. Full replacement of the editable fields.
 * {@code customerCode} is immutable; {@code status} has its own endpoints.
 * {@code version} is required.
 */
@Schema(name = "UpdateCustomerRequest", description = "Full replacement of a customer's editable fields")
public record UpdateCustomerRequest(

        @NotNull CustomerType customerType,
        @Size(max = 150) String companyName,

        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @NotBlank @Size(max = 100) String lastName,

        @NotBlank
        @Pattern(regexp = "^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String mobile,
        @Pattern(regexp = "^$|^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number")
        String alternateMobile,

        @Email @Size(max = 255) String email,

        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "must be a valid 15-character GSTIN")
        String gstNumber,

        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$", message = "must be a valid 10-character PAN")
        String panNumber,

        boolean whatsappEnabled,
        boolean smsEnabled,
        boolean emailEnabled,

        @NotNull @PositiveOrZero
        @Schema(description = "Version last read; a stale value returns 409") Long version
) {
}
