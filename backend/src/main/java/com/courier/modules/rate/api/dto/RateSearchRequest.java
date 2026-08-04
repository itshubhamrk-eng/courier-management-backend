package com.courier.modules.rate.api.dto;

import com.courier.modules.rate.domain.RateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Query parameters of {@code GET /api/v1/rates}, bound as a parameter object. */
@Schema(name = "RateSearchRequest", description = "Rate search filters")
public record RateSearchRequest(
        Set<UUID> routeId,
        Set<UUID> serviceTypeId,
        Set<UUID> packageTypeId,
        Set<UUID> paymentModeId,
        Set<RateStatus> status,
        @Size(max = 100)
        @Schema(description = "Free text over rate code and rate name") String search
) {
    public static RateSearchRequest empty() {
        return new RateSearchRequest(null, null, null, null, null, null);
    }
}
