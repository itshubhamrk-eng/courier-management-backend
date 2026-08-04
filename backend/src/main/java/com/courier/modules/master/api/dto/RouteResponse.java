package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.DistanceUnit;
import com.courier.modules.master.domain.MasterStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A route.
 *
 * <p>The two branch names are resolved through {@code BranchLookupPort} so a route list
 * reads "Pune Main to Mumbai Central" without the client fetching every branch. Null when
 * the branch could not be read — not invented.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "RouteResponse", description = "Route between two branches")
public record RouteResponse(
        UUID id, UUID companyId, String code, String name, String description,
        MasterStatus status, Integer displayOrder,
        UUID bookingBranchId, String bookingBranchName,
        UUID deliveryBranchId, String deliveryBranchName,
        BigDecimal distanceKm, DistanceUnit distanceUnit,
        Integer transitDays, Integer transitHours, String via,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
