package com.courier.modules.freight.api.dto;

import com.courier.modules.freight.domain.FreightFactorStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/** Query parameters of {@code GET /api/v1/freight-factors}, bound as a parameter object. */
@Schema(name = "FreightFactorSearchRequest", description = "Freight factor search filters")
public record FreightFactorSearchRequest(Set<FreightFactorStatus> status) {

    public static FreightFactorSearchRequest empty() {
        return new FreightFactorSearchRequest(null);
    }
}
