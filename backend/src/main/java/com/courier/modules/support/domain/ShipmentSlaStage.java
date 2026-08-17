package com.courier.modules.support.domain;

/**
 * The five shipment lifecycle transitions the SLA sweep watches. Each is the outbound
 * leg of the status named — e.g. {@code BOOKING_TO_LOADING_SHEET} is how long a
 * {@code BOOKED} shipment may go without a loading sheet.
 */
public enum ShipmentSlaStage {
    BOOKING_TO_LOADING_SHEET,
    LOADING_SHEET_TO_THC,
    THC_TO_INSCAN,
    INSCAN_TO_DRS,
    DRS_TO_DELIVERY
}
