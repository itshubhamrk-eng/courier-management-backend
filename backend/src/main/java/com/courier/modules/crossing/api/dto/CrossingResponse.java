package com.courier.modules.crossing.api.dto;

import com.courier.modules.crossing.domain.CrossingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Flat, like {@code TopupRequestResponse} — ids only, no name resolution here; the
 *  frontend already carries a branch directory for labels. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "CrossingResponse", description = "A shipment's crossing through an intermediate branch")
public record CrossingResponse(
        UUID id,
        UUID companyId,
        UUID shipmentId,
        Integer sequenceOrder,
        UUID branchId,
        CrossingStatus status,
        BigDecimal charge,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        Long version
) {
}
