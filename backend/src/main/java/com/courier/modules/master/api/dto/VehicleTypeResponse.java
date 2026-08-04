package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.MasterStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A vehicle type. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "VehicleTypeResponse", description = "Vehicle type")
public record VehicleTypeResponse(
        UUID id, UUID companyId, String code, String name, String description,
        MasterStatus status, Integer displayOrder,
        BigDecimal capacityKg, BigDecimal capacityCft, Integer wheelCount, boolean requiresPermit,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
