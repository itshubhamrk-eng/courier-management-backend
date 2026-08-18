package com.courier.modules.followup.domain;

/**
 * What a follow-up is about — used both as {@link FollowUp#getReferenceType()} (what
 * {@code referenceId} points at) and as {@link FollowUp#getFollowUpType()} (the
 * category shown on the list/dashboard). The two are deliberately the same enum: a
 * follow-up "about a shipment" and a follow-up "of type shipment" are the same idea
 * asked two ways, not two different vocabularies.
 */
public enum FollowUpType {
    CUSTOMER,
    SHIPMENT,
    DELIVERY,
    PAYMENT,
    EXCEPTION,
    GENERAL
}
