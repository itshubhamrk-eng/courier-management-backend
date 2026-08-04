package com.courier.modules.master.api.dto;

import com.courier.modules.master.domain.MasterStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * A country, in full.
 *
 * <p>There is no separate summary projection on any master list. A master row is a dozen
 * short fields; a second, narrower record would double the DTOs for a payload saving of
 * nothing, and the list and detail screens would then drift apart.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "CountryResponse", description = "Country")
public record CountryResponse(
        UUID id, UUID companyId, String code, String name, String description,
        MasterStatus status, Integer displayOrder,
        String isoCode2, String isoCode3, String dialCode, String currencyCode,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
