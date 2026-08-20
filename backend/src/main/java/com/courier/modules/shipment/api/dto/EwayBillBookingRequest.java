package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The E-Way Bill data a booking screen supplies inline, in the same call that books or
 * edits a shipment — required whenever {@code invoiceValue} exceeds the company's own
 * mandatory threshold; optional and simply attached-if-given otherwise. Its own
 * {@code invoiceValue} is not repeated here — the shipment's own {@code invoiceValue}
 * carries it, and {@code ShipmentServiceImpl} copies it across when creating the row.
 */
@Schema(name = "EwayBillBookingRequest", description = "E-Way Bill data supplied inline with a booking")
public record EwayBillBookingRequest(
        @Size(max = 30) String ewayBillNumber,
        @NotBlank @Size(max = 50) String invoiceNumber,
        @NotNull LocalDate invoiceDate,
        @Schema(description = "INVOICE (default) | BILL_OF_SUPPLY | DELIVERY_CHALLAN | OTHERS")
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
