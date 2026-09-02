package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.MasterStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** A pincode. {@code areaName} is resolved from the parent for display. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "PincodeResponse", description = "Pincode within an area")
public record PincodeResponse(
        UUID id, UUID companyId, String code, String name, String description,
        MasterStatus status, Integer displayOrder,
        UUID areaId, String areaName,
        boolean serviceable, boolean codAvailable, boolean prepaidAvailable,
        boolean pickupAvailable, String zone, boolean odaApplicable,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
