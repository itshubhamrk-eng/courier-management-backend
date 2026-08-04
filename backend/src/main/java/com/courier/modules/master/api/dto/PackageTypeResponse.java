package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.MasterStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A package type. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PackageTypeResponse", description = "Package type")
public record PackageTypeResponse(
        UUID id, UUID companyId, String code, String name, String description,
        MasterStatus status, Integer displayOrder,
        boolean documentType, boolean fragileByDefault, BigDecimal maxWeightKg,
        BigDecimal defaultLengthCm, BigDecimal defaultWidthCm, BigDecimal defaultHeightCm,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
