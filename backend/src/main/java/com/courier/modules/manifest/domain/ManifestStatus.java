package com.courier.modules.manifest.domain;

/**
 * A manifest's own lifecycle — distinct from, and much shorter than, the shipment status
 * graph it groups: {@code CREATED} (shipments are being scanned onto it) -&gt;
 * {@code DISPATCHED} (vehicle and driver assigned, gone from the branch) -&gt;
 * {@code COMPLETED} (informational only — nothing in this module sets it yet; a future
 * "close the manifest once every shipment has arrived" step would).
 */
public enum ManifestStatus {
    CREATED,
    DISPATCHED,
    COMPLETED
}
