package com.courier.modules.shipment.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record InScanRequest(
        @NotNull UUID receivingBranchId,
        @NotEmpty List<String> trackingNumbers,
        /** Descriptive only — see {@code ShipmentService.inScan}'s own doc. */
        String manifestNumber,
        /** Non-empty raises an automatic shortage ticket. See {@code ShipmentService.inScan}. */
        List<String> missingTrackingNumbers,
        /** Optional — recorded on every received shipment's status-history row in place of
         *  the default "In scan" text. */
        @Size(max = 500) String remarks,
        /** Optional — URL from {@code /shipment-movement/in-scan-upload}, attached as a
         *  shared photo asset to every shipment this call actually receives. */
        @Size(max = 1000) String photoUrl
) {
}
