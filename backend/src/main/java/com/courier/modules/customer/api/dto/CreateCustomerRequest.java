package com.courier.modules.customer.api.dto;

import com.courier.modules.customer.domain.CustomerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/customers}. {@code COMPANY_ADMIN}, {@code BRANCH_MANAGER}
 * or an operator booking at the counter.
 *
 * <p>Not accepted: {@code companyId} (from the JWT), {@code status} (a new customer
 * starts ACTIVE). {@code customerCode} is optional — blank generates one. GST is
 * mandatory only when {@code customerType} is {@code BUSINESS}, enforced server-side
 * because it depends on another field.
 */
@Schema(name = "CreateCustomerRequest", description = "New customer within the caller's company")
public record CreateCustomerRequest(

        @Size(max = 50)
        @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9_ -]{1,48}[A-Za-z0-9]$",
                message = "3-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(description = "Blank generates one", example = "CUST7K4M9PX2") String customerCode,

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
        @Schema(description = "Mandatory when customerType is BUSINESS", example = "27ABCDE1234F1Z5")
        String gstNumber,

        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$", message = "must be a valid 10-character PAN")
        String panNumber,

        @Schema(description = "Communication preference — defaults to true when omitted") Boolean whatsappEnabled,
        @Schema(description = "Communication preference — defaults to true when omitted") Boolean smsEnabled,
        @Schema(description = "Communication preference — defaults to true when omitted") Boolean emailEnabled
) {
}
