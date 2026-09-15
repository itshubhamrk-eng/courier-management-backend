package com.courier.modules.ewaybill.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Every field of an E-Way Bill except its shipment, its consignor/consignee identity
 * and its optimistic-lock version — the one place this shape is declared. Reused by
 * three call sites that would otherwise repeat the same fields: {@code
 * CreateEwayBillCommand} (standalone {@code POST /eway-bills}, a manual attach where
 * {@code ewayBillNumber} may be typed directly), {@code UpdateEwayBillCommand}
 * (standalone {@code PUT}), and Shipment Booking's own {@code CreateShipmentCommand}/
 * {@code UpdateShipmentCommand}.
 *
 * <p>For the booking-time auto-generation path ({@code EwayBillServiceImpl
 * .generatePartAForShipment}), {@code ewayBillNumber}/{@code transporterId}/
 * {@code vehicleNumber}/{@code distance}/{@code validFrom}/{@code validUntil} are
 * ignored — those are provider-issued or set at Manifest dispatch, never typed by the
 * booking screen. They stay on this record only for the standalone manual-attach path,
 * where an operator may already hold a government-issued number obtained outside this
 * application (e.g. during a provider outage).
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
        String remarks,
        /** GSTIN of the shipment's sender — optional, this codebase does not otherwise
         *  capture GST numbers on {@code Shipment} itself. */
        String consignorGstin,
        /** GSTIN of the shipment's receiver — optional, same reasoning. */
        String consigneeGstin
) {
}
