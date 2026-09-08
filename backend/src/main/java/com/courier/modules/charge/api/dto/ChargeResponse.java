package com.courier.modules.charge.api.dto;

import com.courier.modules.charge.domain.ChargeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full representation of a charge, including its settings — "show associated charge settings". */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ChargeResponse", description = "Charge configuration, in full, with its settings")
public record ChargeResponse(
        UUID id, UUID companyId, String chargeName, UUID serviceTypeId, ChargeStatus status,
        List<ChargeSettingResponse> settings,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
