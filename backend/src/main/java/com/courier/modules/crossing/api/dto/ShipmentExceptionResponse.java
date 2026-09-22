package com.courier.modules.crossing.api.dto;

import com.courier.modules.crossing.domain.ShipmentExceptionStatus;
import com.courier.modules.crossing.domain.ShipmentExceptionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ShipmentExceptionResponse", description = "A Missing/Damaged/Short/Wrong Destination/"
        + "Misrouted/On Hold exception raised against a shipment at a hub")
public record ShipmentExceptionResponse(
        UUID id,
        UUID shipmentId,
        UUID hubBranchId,
        ShipmentExceptionType exceptionType,
        ShipmentExceptionStatus status,
        String remarks,
        UUID raisedBy,
        Instant raisedAt,
        UUID resolvedBy,
        Instant resolvedAt,
        String resolutionRemarks,
        UUID ticketId
) {
}
