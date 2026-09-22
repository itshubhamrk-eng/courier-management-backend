package com.courier.modules.crossing.application;

/**
 * The nine figures Hub Operations' own dashboard shows for one hub — see
 * {@code HubOperationsService#dashboard}.
 *
 * @param todaysInbound      shipments in-scanned at this hub today (any arrival —
 *                           {@code IN_SCAN} at final destination or a crossing hop)
 * @param pendingInScan      shipments dispatched and headed here, not yet arrived
 * @param shipmentsAtHub     shipments physically here right now, any stage
 * @param pendingSorting     arrived, not yet attached to an outbound Load Sheet
 * @param readyForDispatch   attached to a Load Sheet from this hub, not yet dispatched
 * @param dispatchedToday    manifests booked from this hub, dispatched today
 * @param pendingExceptions  open {@code ShipmentException} rows at this hub
 * @param todaysLoadSheets   manifests booked from this hub, created today (any status)
 */
public record HubDashboardStats(
        long todaysInbound,
        long pendingInScan,
        long shipmentsAtHub,
        long pendingSorting,
        long readyForDispatch,
        long dispatchedToday,
        long pendingExceptions,
        long todaysLoadSheets
) {
}
