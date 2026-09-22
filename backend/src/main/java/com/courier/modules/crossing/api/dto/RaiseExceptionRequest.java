package com.courier.modules.crossing.api.dto;

import com.courier.modules.crossing.domain.ShipmentExceptionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code POST /api/v1/hub-operations/exceptions}. */
@Schema(name = "RaiseExceptionRequest", description = "Raise a Hub Operations exception against a shipment")
public record RaiseExceptionRequest(
        @NotNull UUID shipmentId,
        @NotNull UUID hubBranchId,
        @NotNull ShipmentExceptionType exceptionType,
        @Size(max = 500) String remarks
) {
}
