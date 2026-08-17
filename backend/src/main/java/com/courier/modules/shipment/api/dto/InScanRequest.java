package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record InScanRequest(
        @NotNull UUID receivingBranchId,
        @NotEmpty List<String> trackingNumbers,
        /** Descriptive only — see {@code ShipmentService.inScan}'s own doc. */
        String manifestNumber,
        /** Non-empty raises an automatic shortage ticket. See {@code ShipmentService.inScan}. */
        List<String> missingTrackingNumbers
) {
}
