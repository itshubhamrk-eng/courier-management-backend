package com.courier.modules.crossing.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Body of {@code POST /api/v1/hub-operations/out-scan}. */
@Schema(name = "OutScanRequest", description = "Confirm shipments physically present at a hub before dispatch")
public record OutScanRequest(
        @NotNull UUID manifestId,
        @NotNull UUID hubBranchId,
        @NotEmpty List<String> trackingNumbers
) {
}
