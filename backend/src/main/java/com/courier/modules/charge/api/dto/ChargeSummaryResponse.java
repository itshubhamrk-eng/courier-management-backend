package com.courier.modules.charge.api.dto;

import com.courier.modules.charge.domain.ChargeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * List projection — mirrors {@code GET /api/v1/charges}. No setting count: computing one
 * per row would be an N+1 query for every page of the list; the full settings list is one
 * click away on {@code GET /api/v1/charges/{id}}.
 */
@Schema(name = "ChargeSummaryResponse", description = "Charge, list projection")
public record ChargeSummaryResponse(
        UUID id, String chargeName, UUID serviceTypeId, ChargeStatus status, Long version
) {
}
