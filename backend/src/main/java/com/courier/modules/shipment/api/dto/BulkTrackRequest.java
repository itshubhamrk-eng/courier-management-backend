package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /api/v1/shipments/track/bulk}. Each entry is matched against either
 * {@code trackingNumber} (AWB) or {@code shipmentNumber} — the caller pasting numbers off a
 * courier slip won't reliably know which one they have.
 */
@Schema(name = "BulkTrackRequest", description = "Tracking or shipment numbers to look up together")
public record BulkTrackRequest(
        @NotEmpty @Size(max = 200) List<@NotEmpty @Size(max = 50) String> numbers
) {
}
