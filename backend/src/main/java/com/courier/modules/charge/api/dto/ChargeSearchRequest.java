package com.courier.modules.charge.api.dto;

import com.courier.modules.charge.domain.ChargeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Query parameters of {@code GET /api/v1/charges}, bound as a parameter object. */
@Schema(name = "ChargeSearchRequest", description = "Charge search filters")
public record ChargeSearchRequest(
        Set<UUID> serviceTypeId,
        Set<ChargeStatus> status,
        @Size(max = 100) @Schema(description = "Free text over charge name") String search
) {
    public static ChargeSearchRequest empty() {
        return new ChargeSearchRequest(null, null, null);
    }
}
