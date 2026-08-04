package com.courier.modules.shipment.domain;

/**
 * The content category booked, independent of {@code packageTypeId} (the container: an
 * envelope, box or pallet) and of {@code serviceTypeId} (the speed sold). The brief names
 * a bare "shipmentType" field with no vocabulary of its own; this is the standard
 * three-way split the Indian courier industry uses (a non-document shipment above a
 * threshold value needs an e-way bill, a document does not, and cargo is booked and rated
 * differently again), the same "small honest judgement call" precedent
 * {@code MEMORY/modules/rate-master.md}'s optional {@code bookingDate} and
 * {@code pricing-engine.md}'s discount fields already document.
 */
public enum ShipmentType {
    DOCUMENT,
    NON_DOCUMENT,
    CARGO
}
