package com.courier.modules.ewaybill.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Every field of an E-Way Bill except its shipment and its optimistic-lock version — the
 * one place that shape is declared. Reused by three call sites that would otherwise repeat
 * the same fourteen fields: {@code CreateEwayBillCommand} (standalone {@code POST
 * /eway-bills}), {@code UpdateEwayBillCommand} (standalone {@code PUT}), and Shipment
 * Booking's own {@code CreateShipmentCommand}/{@code UpdateShipmentCommand}, which carry
 * one of these inline so a mandatory E-Way Bill can be supplied in the same booking call
 * that needs it validated before AWB generation.
 *
 * @param documentType one of {@code INVOICE}/{@code BILL_OF_SUPPLY}/{@code DELIVERY_CHALLAN}/
 *                     {@code OTHERS} as a string on the wire, null defaults to {@code INVOICE}
 */
public record EwayBillDataCommand(
        String ewayBillNumber,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal invoiceValue,
        String documentType,
        String documentNumber,
        LocalDate documentDate,
        String transporterId,
        String vehicleNumber,
        Integer distance,
        Instant validFrom,
        Instant validUntil,
        String documentUrl,
        String remarks
) {
}
