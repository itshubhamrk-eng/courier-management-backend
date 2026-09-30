package com.courier.modules.shipment.infrastructure;

import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.shared.domain.TimeOrderedUuid;
import com.courier.modules.support.domain.ShipmentSlaPort;
import com.courier.modules.support.domain.ShipmentSlaStage;
import com.courier.modules.support.domain.ShipmentSlaThresholds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Answers Ticket Support's "which shipments have breached their lifecycle SLA" question,
 * backed by {@code shipments}/{@code shipment_status_history}. Same seam as {@code
 * company.infrastructure.TicketDirectory}: Support owns {@link ShipmentSlaPort}, this
 * module supplies the adapter, so Support never imports {@code Shipment}/{@code
 * ShipmentStatus} directly.
 */
@Component
@RequiredArgsConstructor
public class ShipmentSlaAdapter implements ShipmentSlaPort {

    private final ShipmentRepository shipmentRepository;

    private static final java.util.Map<String, ShipmentSlaStage> STAGE_BY_STATUS = java.util.Map.of(
            "BOOKED", ShipmentSlaStage.BOOKING_TO_LOADING_SHEET,
            "MANIFEST_CREATED", ShipmentSlaStage.LOADING_SHEET_TO_THC,
            "DISPATCHED", ShipmentSlaStage.THC_TO_INSCAN,
            "IN_SCAN", ShipmentSlaStage.INSCAN_TO_DRS,
            "OUT_FOR_DELIVERY", ShipmentSlaStage.DRS_TO_DELIVERY);

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> findBreachCandidates(UUID companyId, ShipmentSlaThresholds thresholds, Instant asOf) {
        List<ShipmentRepository.ShipmentSlaCandidateRow> rows = shipmentRepository.findSlaBreachCandidates(
                companyId, asOf,
                thresholds.bookingToLoadingSheetHours(), thresholds.loadingSheetToThcHours(),
                thresholds.thcToInscanHours(), thresholds.inscanToDrsHours(), thresholds.drsToDeliveryHours());

        return rows.stream()
                .map(row -> {
                    Instant enteredAt = toInstant(row.getEnteredAt());
                    return new Candidate(
                        TimeOrderedUuid.fromBytes(row.getShipmentId()),
                        row.getTrackingNumber(),
                        row.getBranchId() == null ? null : TimeOrderedUuid.fromBytes(row.getBranchId()),
                        STAGE_BY_STATUS.get(row.getStatus()),
                        enteredAt,
                        Duration.between(enteredAt, asOf).toHours());
                })
                .toList();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        if (value instanceof java.time.LocalDateTime l) {
            return l.toInstant(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unexpected SLA enteredAt type: " + value);
    }
}
