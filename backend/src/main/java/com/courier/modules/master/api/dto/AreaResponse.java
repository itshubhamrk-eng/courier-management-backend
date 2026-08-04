package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.MasterStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** An area. {@code cityName} is resolved from the parent for display. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "AreaResponse", description = "Area within a city")
public record AreaResponse(
        UUID id, UUID companyId, String code, String name, String description,
        MasterStatus status, Integer displayOrder,
        UUID cityId, String cityName,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
