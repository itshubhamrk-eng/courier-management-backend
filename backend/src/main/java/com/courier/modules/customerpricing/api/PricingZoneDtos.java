package com.courier.modules.customerpricing.api;

import com.courier.modules.customerpricing.domain.ZoneStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Wire contract of {@code /api/v1/pricing-zones}. */
public final class PricingZoneDtos {

    private PricingZoneDtos() {
    }

    public record LocationRequest(@NotNull UUID districtId, UUID cityId) {
    }

    public record CreateRequest(
            @NotNull UUID customerId,
            @NotBlank @Size(max = 100) String zoneName,
            @Size(max = 255) String description,
            @Valid List<LocationRequest> locations) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 100) String zoneName,
            @Size(max = 255) String description,
            @NotNull @PositiveOrZero Long version) {
    }

    public record LocationResponse(UUID id, UUID districtId, String districtName, UUID cityId, String cityName) {
    }

    public record ZoneResponse(
            UUID id, UUID customerId, String customerName, String zoneName, String description,
            ZoneStatus status, boolean hasRate, List<LocationResponse> locations,
            Instant createdDate, Instant updatedDate, Long version) {
    }
}
