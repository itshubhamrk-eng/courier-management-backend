package com.courier.modules.charge.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code PUT /api/v1/charges/{id}}. {@code version} required; a stale value returns 409. */
@Schema(name = "UpdateChargeRequest", description = "Full replacement of a charge's editable fields")
public record UpdateChargeRequest(

        @NotBlank @Size(max = 150) String chargeName,

        @NotNull UUID serviceTypeId,

        @NotNull Long version
) {
}
