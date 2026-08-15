package com.courier.modules.shipment.domain;

/** What a {@link ShipmentAsset} was captured for. */
public enum ShipmentAssetType {
    /** An image attached during Shipment Booking, before the shipment leaves the branch. */
    BOOKING,
    /** Proof Of Delivery — a photo or signature capture at {@code deliver()} time. */
    POD
}
