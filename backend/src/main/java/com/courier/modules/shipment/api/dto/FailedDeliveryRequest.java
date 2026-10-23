package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.DeliveryFailureReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "FailedDeliveryRequest")
public record FailedDeliveryRequest(
        @NotNull(message = "Choose why the delivery failed")
        DeliveryFailureReason reason,
        @Size(max = 400)
        @Schema(description = "Required when reason is OTHER")
        String remarks
) {
}
