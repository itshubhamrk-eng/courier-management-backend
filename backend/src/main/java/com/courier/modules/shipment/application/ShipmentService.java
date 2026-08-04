package com.courier.modules.shipment.application;

import com.courier.modules.shipment.application.command.CreateShipmentCommand;
import com.courier.modules.shipment.application.command.UpdateShipmentCommand;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentDocument;
import com.courier.modules.shipment.domain.ShipmentItem;
import com.courier.modules.shipment.domain.ShipmentStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shipment Booking use cases — the core transaction of the platform.
 *
 * <p>This module does not re-decide anything Customer, Master, the Pricing Engine or
 * Branch Wallet already own: it validates the sender/receiver/addresses exist (Customer),
 * lets the Pricing Engine validate the route/serviceability/rate and price the booking in
 * one call, and — for a PREPAID booking — debits the branch wallet through the seam
 * {@code WalletService.debitForBooking} adds. See {@code MEMORY/modules/shipment-booking.md}.
 *
 * <p><b>Audiences:</b>
 * <ul>
 *   <li>{@code COMPANY_ADMIN}, {@code BRANCH_MANAGER}, {@code OPERATOR} — book, update
 *       (while still {@code BOOKED}), cancel and upload documents.</li>
 *   <li>Any other authenticated user of the company — read only.</li>
 * </ul>
 * A {@code SUPER_ADMIN} never reaches a shipment at all — it is a company's own
 * operational record, the same invariant every other business module in this project
 * asserts.
 */
public interface ShipmentService {

    /**
     * Books a shipment: validates sender/receiver/addresses, computes the weight from the
     * item grid, prices it through the Pricing Engine, generates the AWB and shipment
     * number, persists everything, writes the initial {@code BOOKED} status history entry
     * — and, for a PREPAID payment mode, checks the branch wallet can afford it before
     * committing, then debits it once the booking has committed.
     *
     * @throws com.courier.shared.exception.BusinessRuleException any validation failure —
     *         see the module doc for the full list, most of which are the Pricing
     *         Engine's own refusals surfacing unchanged
     */
    Shipment create(CreateShipmentCommand command);

    /**
     * Full replacement of the editable fields, re-pricing the shipment. Only while the
     * shipment is still {@code BOOKED} — once it has moved past that, the record is no
     * longer a draft. Does not touch the wallet: see the module doc's honesty note on
     * re-pricing an already-debited PREPAID booking.
     */
    Shipment update(UUID id, UpdateShipmentCommand command);

    Shipment getById(UUID id);

    Shipment getByTrackingNumber(String trackingNumber);

    Page<Shipment> search(ShipmentCriteria criteria, Pageable pageable);

    /**
     * Net amount per shipment, for the list row — batch-fetched (one query for the whole
     * page, not one per row) since {@code netAmount} lives on the separate
     * {@code shipment_charges} row, not on {@code Shipment} itself. A shipment missing
     * from the returned map has no charge record.
     */
    Map<UUID, BigDecimal> netAmountsFor(Collection<UUID> shipmentIds);

    /**
     * Cancels a shipment. Refused once it has left the branch —
     * {@link com.courier.modules.shipment.domain.ShipmentStatus#isCancellable()} is the
     * single source of truth. Does not reverse a PREPAID debit; see the module doc.
     */
    Shipment cancel(UUID id, String remarks);

    List<ShipmentItem> getItems(UUID shipmentId);

    ShipmentCharges getCharges(UUID shipmentId);

    List<ShipmentStatusHistory> getHistory(UUID shipmentId);

    List<ShipmentDocument> getDocuments(UUID shipmentId);

    ShipmentDocument addDocument(UUID shipmentId, AddDocumentCommand command);

    /** @param documentType one of the five the brief names, as a string on the wire */
    record AddDocumentCommand(String documentType, String documentName, String documentUrl,
                              String remarks) {
    }

    /** The persisted charge row plus the resolved route/rate codes, for a display-ready response. */
    record ShipmentCharges(com.courier.modules.shipment.domain.ShipmentCharge charge,
                           String matchedRouteCode, String matchedRateCode) {
    }

    // =================================================================== Shipment Movement (V19)
    //
    // Manifest orchestrates both "create a manifest" and "dispatch a manifest" — it is the
    // only module that needs to call the other (manifest -> shipment): a shipment already
    // knows nothing about manifests except its own manifestId, so there is no reverse
    // arrow, and no service-to-service Spring circular dependency between the two modules.

    /**
     * Called only by {@code ManifestServiceImpl.create} — attaches a {@code BOOKED}
     * shipment travelling exactly {@code expectedBookingBranchId} ->
     * {@code expectedDeliveryBranchId} to a newly created manifest and transitions it to
     * {@code MANIFEST_CREATED}. Not exposed on its own REST endpoint.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the shipment is not
     *         {@code BOOKED}, or travels a different lane than the manifest
     */
    Shipment attachToManifest(UUID shipmentId, UUID manifestId, UUID expectedBookingBranchId,
                             UUID expectedDeliveryBranchId);

    /** Read-only — {@code ManifestServiceImpl.dispatch} uses this to enforce "at least one
     * shipment on the manifest" before it will dispatch. Out Scan folded into
     * {@code MANIFEST_CREATED} itself (V20, on direct request) — adding a shipment to a
     * manifest already is the "out scan created" milestone, so this simply reads every
     * {@code MANIFEST_CREATED} shipment on the manifest rather than a separate scanned
     * subset. */
    List<Shipment> findManifestCreatedShipments(UUID manifestId);

    /**
     * Called only by {@code ManifestServiceImpl.dispatch}, after the manifest itself has
     * already been validated and moved to {@code DISPATCHED} — moves every given shipment
     * (already established to be its manifest's {@code MANIFEST_CREATED} shipments) to
     * {@code DISPATCHED} too, recording the vehicle and branch on each history entry.
     */
    List<Shipment> transitionToDispatched(List<UUID> shipmentIds, UUID manifestId, UUID vehicleId,
                                          UUID bookingBranchId);

    /**
     * Receives every tracking number at {@code receivingBranchId}. Each must resolve to
     * a {@code DISPATCHED} shipment whose {@code deliveryBranchId} equals
     * {@code receivingBranchId} — the brief's own "Receiving Branch must match Delivery
     * Branch" rule. Per-item result, same "bulk operation reports per-row" shape
     * {@code BranchService.assignUsers} already uses.
     */
    BulkMovementResult inScan(UUID receivingBranchId, List<String> trackingNumbers);

    /**
     * Assigns a delivery user to every shipment id, each of which must be {@code IN_SCAN}.
     * Creates or replaces that shipment's {@code DeliveryAssignment} row and moves it to
     * {@code OUT_FOR_DELIVERY}. Per-item result, same shape as {@link #inScan}.
     */
    BulkMovementResult assignOutForDelivery(Collection<UUID> shipmentIds, UUID deliveryUserId);

    /**
     * Closes a delivery: captures receiver name (required), remarks, and the optional
     * OTP/signature/photo, moves the shipment to {@code DELIVERED}.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the shipment is not
     *         {@code OUT_FOR_DELIVERY}
     */
    Shipment deliver(UUID shipmentId, DeliverCommand command);

    /** Oldest first, one step per status the shipment has actually reached — see {@code getHistory}
     * for the raw append-only log this is built from. */
    List<TimelineStep> timeline(UUID shipmentId);

    record DeliverCommand(String receiverName, String remarks, String otp, String signatureUrl,
                          String photoUrl) {
    }

    /** One outcome per input item — {@code message} is null on success. */
    record MovementOutcome(String reference, boolean success, String message) {
    }

    record BulkMovementResult(List<MovementOutcome> results) {
        public long successCount() {
            return results.stream().filter(MovementOutcome::success).count();
        }

        public long failureCount() {
            return results.size() - successCount();
        }
    }

    record TimelineStep(com.courier.modules.shipment.domain.ShipmentStatus status, String label,
                        java.time.Instant changedAt, UUID changedBy, boolean completed) {
    }
}
