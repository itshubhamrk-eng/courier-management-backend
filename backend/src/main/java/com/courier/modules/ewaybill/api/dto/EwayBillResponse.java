package com.courier.modules.ewaybill.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Full representation of one E-Way Bill. Nulls are serialised. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "EwayBillResponse", description = "One E-Way Bill in full")
public record EwayBillResponse(
        UUID id, UUID shipmentId,
        String ewayBillNumber,
        String invoiceNumber, LocalDate invoiceDate, BigDecimal invoiceValue,
        String documentType, String documentNumber, LocalDate documentDate,
        String consignorGstin, String consigneeGstin,
        String transporterId, String vehicleNumber, String transportMode, Integer distance,
        Instant validFrom, Instant validUntil,
        String status,
        String providerName, String providerReference,
        Instant partAGeneratedAt, Instant partBGeneratedAt,
        String lastError, int retryCount,
        String documentUrl, String remarks,
        UUID createdBy, Instant createdDate, UUID updatedBy, Instant updatedDate, Long version
) {
}
