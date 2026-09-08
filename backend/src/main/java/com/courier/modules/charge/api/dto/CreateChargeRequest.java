package com.courier.modules.charge.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code POST /api/v1/charges}. {@code COMPANY_ADMIN} only. A new charge always starts ACTIVE. */
@Schema(name = "CreateChargeRequest", description = "New charge configuration within the caller's company")
public record CreateChargeRequest(

        @NotBlank @Size(max = 150) @Schema(example = "Fuel Surcharge - Express") String chargeName,

        @NotNull UUID serviceTypeId
) {
}
