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
 * @param manifestId       shipments scanned onto this manifest — Shipment Movement's own
 *                         "Search Manifest -&gt; Display Shipments" (Out Scan) and
 *                         Dispatch screens filter by this
 * @param bookingDateFrom  inclusive
 * @param bookingDateTo    inclusive
 * @param search           free text over shipment number and tracking number
 */
public record ShipmentCriteria(
        Set<ShipmentStatus> statuses,
        UUID bookingBranchId,
        UUID deliveryBranchId,
        UUID manifestId,
        LocalDate bookingDateFrom,
        LocalDate bookingDateTo,
        String search
) {

    public static ShipmentCriteria none() {
        return new ShipmentCriteria(null, null, null, null, null, null, null);
    }
}
