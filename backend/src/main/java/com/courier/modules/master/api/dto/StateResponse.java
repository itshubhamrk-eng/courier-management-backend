package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.MasterStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * A state.
 *
 * <p>{@code countryName} is resolved by the mapper from the parent row so the list screen
 * can show "Maharashtra, India" without a second request per row. It is null when the
 * parent could not be read — which is a hint that something is wrong, not a value to
 * invent.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "StateResponse", description = "State within a country")
public record StateResponse(
        UUID id, UUID companyId, String code, String name, String description,
        MasterStatus status, Integer displayOrder,
        UUID countryId, String countryName, String gstStateCode,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
