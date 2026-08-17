package com.courier.modules.support.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the SLA-breach sweep needs to know about shipments — and nothing more. Same seam
 * {@link TicketDirectoryPort} draws for users/branches: this module owns the interface,
 * {@code modules/shipment} supplies the adapter, so Support never imports {@code Shipment}
 * or {@code ShipmentStatus} directly.
 */
public interface ShipmentSlaPort {

    /**
     * One shipment currently sitting past its stage's threshold, not yet checked against
     * {@code shipment_sla_breaches} — the caller (the sweep) is responsible for
     * idempotency, not this port.
     */
    record Candidate(UUID shipmentId, String trackingNumber, UUID branchId,
                     ShipmentSlaStage stage, Instant stageEnteredAt, long hoursElapsed) {
    }

    /**
     * Every in-flight (non-terminal) shipment in {@code companyId} whose time in its
     * current status already exceeds that stage's threshold, as of {@code asOf}.
     */
    List<Candidate> findBreachCandidates(UUID companyId, ShipmentSlaThresholds thresholds, Instant asOf);
}
