package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.DistanceUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code PUT /api/v1/master/routes/{id}}.
 *
 * <p>The branch pair is editable, unlike the code: a company re-pointing a lane at a
 * relocated branch is ordinary. Moving an end requires the new branch to be active.
 */
@Schema(name = "UpdateRouteRequest", description = "Full replacement of a route's editable fields")
public record UpdateRouteRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @NotNull UUID bookingBranchId,
        @NotNull UUID deliveryBranchId,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal distanceKm,
        DistanceUnit distanceUnit,
        @PositiveOrZero Integer transitDays,
        @PositiveOrZero @Max(23) Integer transitHours,
        @Size(max = 255) String via,
        @NotNull @PositiveOrZero Long version
) {
}
