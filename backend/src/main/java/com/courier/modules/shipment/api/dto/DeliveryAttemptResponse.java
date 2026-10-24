package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.DeliveryAttemptOutcome;
import com.courier.modules.shipment.domain.DeliveryFailureReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "DeliveryAttemptResponse")
public record DeliveryAttemptResponse(
        UUID id,
        UUID shipmentId,
        int attemptNo,
        @Schema(description = "Failed attempts allowed; the last one returns the shipment to origin")
        int maxAttempts,
        DeliveryFailureReason reason,
        String remarks,
        @Schema(description = "RETRY: back at the branch for the next DRS. RTO: shipment RETURNED")
        DeliveryAttemptOutcome outcome,
        UUID deliveryUserId,
        UUID deliveryBranchId,
        String drsNumber,
        Instant attemptedAt
) {
}
