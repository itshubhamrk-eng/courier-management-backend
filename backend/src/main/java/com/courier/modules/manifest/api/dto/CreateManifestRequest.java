package com.courier.modules.manifest.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(name = "CreateManifestRequest")
public record CreateManifestRequest(
        @NotNull UUID bookingBranchId,
        @NotNull UUID deliveryBranchId,
        @NotEmpty List<UUID> shipmentIds,
        @Size(max = 500) String remarks
) {
}
