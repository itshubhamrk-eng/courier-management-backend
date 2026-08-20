package com.courier.modules.ewaybill.application.command;

import java.util.UUID;

/** Body of the standalone {@code POST /eway-bills} — attaches an E-Way Bill to an
 *  already-booked shipment (as opposed to the inline one Shipment Booking accepts). */
public record CreateEwayBillCommand(UUID shipmentId, EwayBillDataCommand data) {
}
