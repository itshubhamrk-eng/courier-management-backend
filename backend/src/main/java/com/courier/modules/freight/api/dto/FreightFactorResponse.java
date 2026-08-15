package com.courier.modules.freight.api.dto;

import com.courier.modules.freight.domain.FreightFactorStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Full representation of a freight factor grid cell. Nulls are serialised. Used for
 * both list and detail responses — the entity has too few fields to warrant a separate
 * summary projection like {@code RateSummaryResponse}. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "FreightFactorResponse", description = "Freight factor grid cell")
public record FreightFactorResponse(
        UUID id, UUID companyId,
        BigDecimal fromKm, BigDecimal toKm,
        BigDecimal fromWeight, BigDecimal toWeight,
        BigDecimal factor,
        FreightFactorStatus status,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
