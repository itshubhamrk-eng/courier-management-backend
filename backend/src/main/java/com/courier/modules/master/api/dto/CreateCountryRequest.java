package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/master/countries}. {@code COMPANY_ADMIN} only.
 *
 * <p>Not accepted: {@code companyId} (from the JWT) and {@code status} (a new row starts
 * ACTIVE). The {@code code} pattern is shared by every master list — letters, digits,
 * space, hyphen and underscore, first and last character alphanumeric — and it is
 * uppercased with spaces turned into underscores before it is stored.
 */
@Schema(name = "CreateCountryRequest", description = "New country in the caller's company")
public record CreateCountryRequest(

        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{0,48}[A-Za-z0-9]$",
                message = "2-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "INDIA") String code,

        @NotBlank @Size(max = 150) @Schema(example = "India") String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,

        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "must be an ISO 3166-1 alpha-2 code")
        @Schema(example = "IN") String isoCode2,
        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "must be an ISO 3166-1 alpha-3 code")
        @Schema(example = "IND") String isoCode3,
        @Size(max = 8) @Schema(example = "+91") String dialCode,
        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "must be an ISO 4217 currency code")
        @Schema(example = "INR") String currencyCode
) {
}
