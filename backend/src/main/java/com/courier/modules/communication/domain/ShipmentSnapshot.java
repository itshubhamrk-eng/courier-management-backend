package com.courier.modules.communication.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Everything {@code TemplateRenderer} needs to fill every {@code {{variable}}} the brief
 * lists, plus who to notify. Recipient party is chosen by event, in
 * {@code CommunicationOrchestrator}, not here: booking/dispatch/cancellation notify the
 * sender (they made the booking), receipt/out-for-delivery/delivery notify the receiver
 * (it's arriving at them) — both parties travel on this one snapshot so the orchestrator
 * never needs a second lookup.
 *
 * @param amount       the shipment's persisted net amount (Shipment Booking's own charge
 *                     breakup), never recomputed here
 * @param deliveryDate the shipment's expected delivery date — the actual delivered
 *                     timestamp lives only in {@code shipment_status_history}, not on
 *                     {@code Shipment} itself, so this is the one date this snapshot can
 *                     answer without a second, event-specific query
 * @param podUrl       the latest POD photo URL if one exists yet (null before delivery)
 */
public record ShipmentSnapshot(
        UUID shipmentId,
        String shipmentNumber,
        String trackingNumber,
        String companyName,
        Party sender,
        Party receiver,
        String pickupLocation,
        String deliveryLocation,
        BigDecimal amount,
        LocalDate deliveryDate,
        String podUrl
) {

    /** Sender/receiver as typed on the shipment (plain text, no FK — see
     *  {@code shipment-booking.md}), resolved against the reusable {@code Customer} master
     *  row {@code findOrCreateForBooking} matched by mobile at booking time, if one exists. */
    public record Party(
            String name,
            String contact,
            UUID customerId,
            String email,
            boolean whatsappEnabled,
            boolean smsEnabled,
            boolean emailEnabled
    ) {
    }
}
