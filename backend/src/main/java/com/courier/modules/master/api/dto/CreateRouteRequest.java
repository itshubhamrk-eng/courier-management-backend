package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.DistanceUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/master/routes}.
 *
 * <p>Both branches must belong to the caller's company and be active, and they must
 * differ. Direction matters: Pune to Mumbai and Mumbai to Pune are two routes, and only
 * one route may exist per ordered pair.
 */
@Schema(name = "CreateRouteRequest", description = "New route between two branches")
public record CreateRouteRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{0,48}[A-Za-z0-9]$",
                message = "2-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "PNQ_BOM") String code,
        @NotBlank @Size(max = 150) @Schema(example = "Pune to Mumbai") String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        @NotNull UUID bookingBranchId,
        @NotNull UUID deliveryBranchId,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal distanceKm,
        @Schema(description = "Defaults to KM; the only unit today", example = "KM") DistanceUnit distanceUnit,
        @PositiveOrZero @Schema(description = "Working days in transit; 0 is same day",
                example = "1") Integer transitDays,
        @PositiveOrZero @Max(23) @Schema(description = "Remainder hours on top of transitDays, 0-23",
                example = "4") Integer transitHours,
        @Size(max = 255) @Schema(example = "Lonavala") String via
) {
}
