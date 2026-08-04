package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/master/states/{id}}.
 *
 * <p>The parent <i>is</i> editable — a state filed under the wrong country has to be
 * fixable — but moving it requires the new country to be active, while leaving it alone
 * does not.
 */
@Schema(name = "UpdateStateRequest", description = "Full replacement of a state's editable fields")
public record UpdateStateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @NotNull UUID countryId,
        @Size(max = 4) String gstStateCode,
        @NotNull @PositiveOrZero Long version
) {
}
