package com.courier.modules.ewaybill.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Body of {@code PUT /api/v1/eway-bills/{id}}. Refused once the row is {@code CANCELLED}. */
@Schema(name = "UpdateEwayBillRequest", description = "Full replacement of an E-Way Bill's editable fields")
public record UpdateEwayBillRequest(
        @NotNull Long version,
        @Size(max = 30) String ewayBillNumber,
        @NotBlank @Size(max = 50) String invoiceNumber,
        @NotNull LocalDate invoiceDate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal invoiceValue,
        String documentType,
        @Size(max = 50) String documentNumber,
        LocalDate documentDate,
        @Size(max = 50) String transporterId,
        @Size(max = 20) String vehicleNumber,
        @Min(0) Integer distance,
        Instant validFrom,
        Instant validUntil,
        @Size(max = 1000) String documentUrl,
        @Size(max = 500) String remarks
) {
}
