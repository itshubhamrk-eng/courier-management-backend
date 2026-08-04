package com.courier.modules.shipment.api.dto;

import com.courier.modules.shipment.domain.ShipmentDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ShipmentDocumentResponse", description = "One uploaded document reference")
public record ShipmentDocumentResponse(
        UUID id, ShipmentDocumentType documentType, String documentName, String documentUrl,
        String remarks, UUID createdBy, Instant createdDate
) {
}
