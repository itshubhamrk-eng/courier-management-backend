package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.MasterStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** A city. {@code districtName} is resolved from the parent for display. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "CityResponse", description = "City within a district")
public record CityResponse(
        UUID id, UUID companyId, String code, String name, String description,
        MasterStatus status, Integer displayOrder,
        UUID districtId, String districtName, boolean metro, String cityTier,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
