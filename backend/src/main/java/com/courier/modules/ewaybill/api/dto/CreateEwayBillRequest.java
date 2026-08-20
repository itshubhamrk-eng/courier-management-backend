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
import java.util.UUID;

/** Body of {@code POST /api/v1/eway-bills} — attaches an E-Way Bill to an already-booked
 *  shipment. See {@code CreateShipmentRequest.ewayBill} for the inline booking-time path. */
@Schema(name = "CreateEwayBillRequest", description = "Attach an E-Way Bill to a shipment")
public record CreateEwayBillRequest(
        @NotNull UUID shipmentId,
        @Size(max = 30) String ewayBillNumber,
        @NotBlank @Size(max = 50) String invoiceNumber,
        @NotNull LocalDate invoiceDate,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal invoiceValue,
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
