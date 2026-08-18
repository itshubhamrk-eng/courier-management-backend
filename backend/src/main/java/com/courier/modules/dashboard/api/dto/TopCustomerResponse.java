package com.courier.modules.dashboard.api.dto;

import java.math.BigDecimal;

/**
 * One row of the Top Customers card. Grouped by {@code senderContact} — Shipment Booking
 * carries no {@code Customer} FK (see {@code Shipment}'s own class doc), so a booking-time
 * sender's phone number is the only stable identity a repeat customer has today.
 */
public record TopCustomerResponse(String customerName, String customerContact,
                                   long shipmentCount, BigDecimal revenue) {
}
