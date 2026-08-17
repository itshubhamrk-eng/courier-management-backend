package com.courier.modules.crossing.application;

import com.courier.modules.crossing.domain.CrossingDetail;
import com.courier.modules.crossing.domain.CrossingDetailCriteria;
import com.courier.modules.crossing.domain.CrossingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A shipment's crossing through a sequence of intermediate branches/hubs. Created once,
 * from Shipment Booking, when the booking desk picks a crossing route — there is no
 * standalone "create a crossing" endpoint, the same "no state without a shipment behind
 * it" shape {@code DeliveryAssignment} follows in Shipment Movement.
 */
public interface CrossingService {

    /** One row per {@code branchId}, in order (hop 0 first). Called by {@code
     *  ShipmentServiceImpl.create} in the same transaction as the shipment save. Every
     *  branch must be real; {@code charge} is the whole route's crossing charge, carried
     *  on hop 0 only — there is no per-hop billing today. */
    List<CrossingDetail> createLegs(UUID shipmentId, List<UUID> branchIds, BigDecimal charge);

    /**
     * A shipment physically arrives at {@code branchId} (an in-scan). Marks that hop
     * COMPLETED and returns the branch of the next hop, if any — empty means this was the
     * last hop and the shipment is now headed straight to its own delivery branch.
     * Called by {@code ShipmentServiceImpl.scanOneIn}; a no-op (empty) for a shipment with
     * no crossing route at all.
     */
    Optional<UUID> arriveAt(UUID shipmentId, UUID branchId);

    /** One crossing hop, within the caller's company. Foreign or out-of-scope answers 404. */
    CrossingDetail getById(UUID id);

    /** Paged, filtered — one row per hop, not per shipment. */
    Page<CrossingDetail> search(CrossingDetailCriteria criteria, Pageable pageable);

    /** Moves one hop to a new status. Refused once that hop is terminal
     *  (COMPLETED/CANCELLED) — see {@code CrossingDetail#isTerminal}. */
    CrossingDetail updateStatus(UUID id, CrossingStatus status, String remarks);
}
