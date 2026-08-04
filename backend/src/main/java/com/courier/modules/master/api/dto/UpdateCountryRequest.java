package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/master/countries/{id}}. Full replacement of the editable
 * fields.
 *
 * <p>There is no {@code code} field, and that is the point: the code is immutable because
 * shipments and rate cards quote it, so the request has no way to express a change rather
 * than a change that is silently dropped. {@code status} has its own endpoints.
 */
@Schema(name = "UpdateCountryRequest", description = "Full replacement of a country's editable fields")
public record UpdateCountryRequest(

        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,

        @Pattern(regexp = "^$|^[A-Za-z]{2}$") String isoCode2,
        @Pattern(regexp = "^$|^[A-Za-z]{3}$") String isoCode3,
        @Size(max = 8) String dialCode,
        @Pattern(regexp = "^$|^[A-Za-z]{3}$") String currencyCode,

        @NotNull @PositiveOrZero
        @Schema(description = "Version last read; a stale value returns 409") Long version
) {
}
