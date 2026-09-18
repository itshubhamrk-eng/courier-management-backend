package com.courier.modules.manifest.api.dto;

import com.courier.modules.manifest.domain.DeliveryMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(name = "CreateManifestRequest")
public record CreateManifestRequest(
        @NotNull UUID bookingBranchId,
        @Schema(description = "Required for BRANCH_DELIVERY, refused for "
                + "DIRECT_COMPANY_DELIVERY (see ManifestServiceImpl.create)")
        UUID deliveryBranchId,
        @Schema(description = "Who is responsible for delivery — null defaults to BRANCH_DELIVERY, "
                + "the only mode that existed before this field")
        DeliveryMode deliveryMode,
        @Size(max = 120)
        @Schema(description = "The destination city this Load Sheet is being created for — "
                + "required to attach a freshly BOOKED shipment that has no delivery branch "
                + "resolved yet; not needed for shipments that already carry a real next-stop "
                + "branch (e.g. a crossing hop)")
        String destinationCity,
        @NotEmpty List<UUID> shipmentIds,
        @Size(max = 500) String remarks
) {
}
