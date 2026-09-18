package com.courier.modules.shipment.domain;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for a shipment search. Every field optional; null means "do not
 * constrain". Lives in {@code domain} so controller and service share it, the same
 * convention {@code CustomerCriteria} follows.
 *
 * @param statuses         match any of these statuses
 * @param bookingBranchId  the booking branch
 * @param deliveryBranchId the delivery branch
 * @param currentLocationId where the shipment physically is right now — Loading Sheet's
 *                          "shipments I can manifest from here" query for a crossing hub
 *                          use this instead of {@code bookingBranchId}, since a shipment
 *                          past its first crossing hop is no longer at its booking branch
 * @param nextLocationId    the shipment's next stop (a crossing hub, or the delivery
 *                          branch once every hop is done) — Loading Sheet's "which branch
 *                          can I manifest this shipment to" uses this instead of
 *                          {@code deliveryBranchId} for the same reason
 * @param manifestId       shipments scanned onto this manifest — Shipment Movement's own
 *                         "Search Manifest -&gt; Display Shipments" (Loading Sheet) and
 *                         Dispatch screens filter by this
 * @param bookingDateFrom  inclusive
 * @param bookingDateTo    inclusive
 * @param deliveredDateFrom inclusive, matched against {@code DeliveryAssignment.deliveredAt}
 *                          (day boundaries in UTC) — a Delivery Report filter, not a column
 *                          on {@code Shipment} itself, so the service resolves it via a
 *                          separate id lookup rather than a predicate on this entity
 * @param deliveredDateTo   inclusive
 * @param paymentModeId    the shipment's payment mode (Paid / To-Pay / Credit)
 * @param search           free text over shipment number and tracking number
 * @param toCity           Load Sheet's destination-city match — the resolved destination
 *                         city text stored on the shipment (see {@code Shipment.toCity}),
 *                         for a shipment that has no delivery branch yet
 * @param unassignedDeliveryBranch when true, only shipments with no {@code deliveryBranchId}
 *                         resolved yet — Load Sheet's own eligibility filter, paired with
 *                         {@code toCity}
 */
public record ShipmentCriteria(
        Set<ShipmentStatus> statuses,
        UUID bookingBranchId,
        UUID deliveryBranchId,
        UUID currentLocationId,
        UUID nextLocationId,
        UUID manifestId,
        LocalDate bookingDateFrom,
        LocalDate bookingDateTo,
        LocalDate deliveredDateFrom,
        LocalDate deliveredDateTo,
        UUID paymentModeId,
        String search,
        String toCity,
        Boolean unassignedDeliveryBranch
) {

    public static ShipmentCriteria none() {
        return new ShipmentCriteria(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }
}
