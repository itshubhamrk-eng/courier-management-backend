package com.courier.modules.communication.domain;

/**
 * {@code PENDING} — queued by the listener, not yet picked up by {@code CommunicationDispatchJob}.
 * {@code SENT} — the provider accepted it (has a {@code providerMessageId}); this is as far
 * as a dev/no-op provider or a real provider with no delivery-receipt webhook wired ever
 * gets — see {@code CommunicationLog}'s own doc.
 * {@code DELIVERED} — a provider delivery-receipt callback confirmed it. No webhook endpoint
 * exists yet in this codebase for any of the three channels, so this status, though fully
 * modelled, is not reachable in this dev environment — an honest gap, not a fabricated one.
 * {@code FAILED} — the provider call failed; retried up to the configured attempt cap.
 * {@code CANCELLED} — never attempted on purpose: the channel is disabled (company or
 * customer) or no active template exists for this event+channel.
 */
public enum CommunicationStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
    CANCELLED
}
