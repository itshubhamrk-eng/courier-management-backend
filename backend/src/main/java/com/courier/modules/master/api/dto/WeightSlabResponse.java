package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.MasterStatus;
import com.courier.modules.master.domain.WeightUnit;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A weight slab, {@code [minWeight, maxWeight)}. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "WeightSlabResponse", description = "Weight slab")
public record WeightSlabResponse(
        UUID id, UUID companyId, String code, String name, String description,
        MasterStatus status, Integer displayOrder,
        BigDecimal minWeight, BigDecimal maxWeight, WeightUnit weightUnit,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
