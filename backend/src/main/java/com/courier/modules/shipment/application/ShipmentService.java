package com.courier.modules.shipment.application;

import com.courier.modules.ewaybill.application.EwayBillService;
import com.courier.modules.shipment.application.command.CreateShipmentCommand;
import com.courier.modules.shipment.application.command.UpdateShipmentCommand;
import com.courier.modules.shipment.domain.BranchCommissionSummary;
import com.courier.modules.shipment.domain.BranchPerformanceSummary;
import com.courier.modules.shipment.domain.DeliveryAssignment;
import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentAsset;
import com.courier.modules.shipment.domain.ShipmentCriteria;
import com.courier.modules.shipment.domain.ShipmentDocument;
import com.courier.modules.shipment.domain.ShipmentItem;
import com.courier.modules.shipment.domain.ShipmentStatusHistory;
import com.courier.modules.shipment.domain.ShipmentSummaryStats;
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

    /** POD/delivery data for a shipment — null once it's never been assigned for delivery,
     *  and unset (delivered fields null) until {@link #deliver} closes it out. */
    DeliveryAssignment getDeliveryAssignment(UUID shipmentId);

    Page<Shipment> search(ShipmentCriteria criteria, Pageable pageable);

    /** Unpaged aggregates for the same search — the Booking/Delivery Report summary row. */
    ShipmentSummaryStats summaryStats(ShipmentCriteria criteria);

    /**
     * Commission totals for the same search, grouped by booking branch — the Commission
     * Report's branch-wise summary table. One row per booking branch with at least one
     * matching, charged shipment; a branch with no charge record on any matching shipment
     * is simply absent, not a zero row.
     */
    List<BranchCommissionSummary> commissionSummary(ShipmentCriteria criteria);

    /**
     * Shipment volume and outcome totals for the same search, grouped by booking branch —
     * the Branch Performance Report's per-branch row. One row per booking branch with at
     * least one matching shipment.
     */
    List<BranchPerformanceSummary> branchPerformance(ShipmentCriteria criteria);

    /**
     * Net amount per shipment, for the list row — batch-fetched (one query for the whole
     * page, not one per row) since {@code netAmount} lives on the separate
     * {@code shipment_charges} row, not on {@code Shipment} itself. A shipment missing
     * from the returned map has no charge record.
     */
    Map<UUID, BigDecimal> netAmountsFor(Collection<UUID> shipmentIds);

    /**
     * The commission breakdown (V28) per shipment, for the list row and the Booking/Delivery
     * Report exports — batch-fetched the same way as {@link #netAmountsFor}, since it lives
     * on the same {@code shipment_charges} row. A shipment missing from the returned map has
     * no charge record.
     */
    Map<UUID, com.courier.modules.shipment.domain.ShipmentCharge> chargesFor(Collection<UUID> shipmentIds);

    /**
     * Delivered-at per shipment, for the Delivery Report's list row — batch-fetched the
     * same way as {@link #netAmountsFor}, since {@code deliveredAt} lives on the separate
     * {@code DeliveryAssignment} row. A shipment missing from the returned map has not
     * been delivered (or has no assignment at all).
     */
    Map<UUID, java.time.Instant> deliveredAtFor(Collection<UUID> shipmentIds);

    /**
     * Every shipment scanned onto any of these manifests, within the caller's company —
     * the THC Report's shipment-count/weight/package-count aggregate, batch-fetched the
     * same "one query, not one per row" way as {@link #netAmountsFor}.
     */
    List<Shipment> findByManifestIds(Collection<UUID> manifestIds);

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

    /** The shipment's current E-Way Bill, if it has one — see
     *  {@code EwayBillService.findLatestForShipment}. Empty when it never needed or never
     *  received one. */
    java.util.Optional<EwayBillService.EwayBillSnapshot> getEwayBill(UUID shipmentId);

    /** Every uploaded image of this shipment (booking photo, POD photo/signature), newest
     *  first — see {@link ShipmentAsset}. */
    List<ShipmentAsset> getAssets(UUID shipmentId);

    /**
     * Stores a shipment photo — taken during booking, before the shipment leaves the
     * branch — in the configured object store and records it as a {@code BOOKING} asset.
     * Unlike {@link #uploadPodFile}, this persists the reference itself rather than
     * handing the URL back for a later call to fold in: a booking image is not part of any
     * state-machine step, so there is nothing else to wait for.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the content type is not
     *         an accepted image, or no storage backend is configured
     */
    String uploadShipmentImage(UUID shipmentId, UploadShipmentImageCommand command);

    record UploadShipmentImageCommand(byte[] content, String filename, String contentType) {
    }

    /**
     * Persists a POD asset (photo or signature) immediately — the seam POD Auto
     * Verification ({@code com.courier.modules.pod}) uses to record the captured document
     * before the delivery decision is made, unlike {@link #deliver}'s own asset recording
     * which only fires once delivery actually commits. Requires no particular shipment
     * status — verification can run against an {@code OUT_FOR_DELIVERY} shipment that has
     * not yet been decided.
     *
     * @param kind {@code PHOTO} or {@code SIGNATURE}
     * @return the persisted asset, whose id is the {@code podDocumentId} the caller stamps
     *         onto its own verification record
     */
    ShipmentAsset attachPodAsset(UUID shipmentId, String kind, String url);

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

    /**
     * Called only by {@code ManifestServiceImpl.removeShipment} — the inverse of
     * {@link #attachToManifest}. Clears {@code manifestId} and reverts the shipment to
     * {@code BOOKED} so it can be picked up by a future manifest.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the shipment is not
     *         {@code MANIFEST_CREATED}, or not currently on {@code manifestId}
     */
    Shipment detachFromManifest(UUID shipmentId, UUID manifestId);

    /** Read-only — {@code ManifestServiceImpl.dispatch} uses this to enforce "at least one
     * shipment on the manifest" before it will dispatch. Loading Sheet folded into
     * {@code MANIFEST_CREATED} itself (V20, on direct request) — adding a shipment to a
     * manifest already is the "loading sheet created" milestone, so this simply reads every
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
     *
     * @param manifestNumber          purely descriptive — only used in a shortage ticket's
     *                                subject/description, never validated or looked up
     * @param missingTrackingNumbers  the operator's own explicit "not physically on this
     *                                THC" checklist answer (In Scan's unchecked rows) —
     *                                when non-empty, auto-raises a Support ticket (category
     *                                "Shipment Issue", HIGH priority) against the company so
     *                                a short receipt is never silently lost. Null/empty
     *                                raises nothing, same behaviour as before this existed.
     */
    BulkMovementResult inScan(UUID receivingBranchId, List<String> trackingNumbers, String manifestNumber,
                              List<String> missingTrackingNumbers);

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

    /**
     * Stores one POD file (photo or signature capture) in the configured object store
     * and returns its URL — the caller then passes that URL into {@link #deliver} as
     * {@code signatureUrl}/{@code photoUrl}, the same "URL is what deliver() takes"
     * contract as before, now backed by a real upload instead of a caller-supplied link.
     *
     * @throws com.courier.shared.exception.BusinessRuleException {@code kind} is not
     *         {@code PHOTO}/{@code SIGNATURE}, the content type is not an accepted
     *         image/video/PDF, or no storage backend is configured
     */
    String uploadPodFile(UUID shipmentId, UploadPodFileCommand command);

    /** Oldest first, one step per status the shipment has actually reached — see {@code getHistory}
     * for the raw append-only log this is built from. */
    List<TimelineStep> timeline(UUID shipmentId);

    /**
     * One row per DRS "run" — a delivery user + delivery branch + calendar day, grouped
     * from {@code DeliveryAssignment} rows (there is still no separate DRS/batch table; a
     * run's identity is still this grouping, not the number — see {@code
     * MEMORY/modules/shipment-movement.md}). Since V31, each {@link #assignOutForDelivery}
     * call stamps a unique {@code drsNumber} ({@code "DRS" + 6-digit serial}) on every row
     * it touches; the summary surfaces the most recent one in the group, null for runs made
     * entirely of rows that predate that column. {@code from}/{@code to} default to the
     * trailing 30 days when null. {@code deliveryBranchId} restricts to one branch's runs
     * when given (a {@code BRANCH_MANAGER}'s own branch) — filtered server-side so a caller
     * scoped to one branch never receives another branch's rows over the wire.
     */
    List<DrsSummary> listDrs(java.time.LocalDate from, java.time.LocalDate to, UUID deliveryBranchId);

    /** Every shipment on one DRS run — same grouping key {@link #listDrs} used to build the row. */
    DrsDetail getDrsDetail(UUID deliveryUserId, UUID deliveryBranchId, java.time.LocalDate runDate);

    /** {@code drsNumber} is the most recently generated number among the run's assignments
     *  (null for runs made entirely of pre-V31 rows) — the grouping key itself is still
     *  delivery user + delivery branch + calendar day, unchanged. */
    record DrsSummary(UUID deliveryUserId, UUID deliveryBranchId, java.time.LocalDate runDate,
                      String drsNumber, int shipmentCount, int deliveredCount, int pendingCount) {
    }

    record DrsShipmentRow(UUID shipmentId, String shipmentNumber, String trackingNumber,
                          String receiverName, String receiverContact, UUID paymentModeId,
                          BigDecimal netAmount, com.courier.modules.shipment.domain.ShipmentStatus status,
                          java.time.Instant deliveredAt) {
    }

    record DrsDetail(UUID deliveryUserId, UUID deliveryBranchId, java.time.LocalDate runDate,
                     String drsNumber, List<DrsShipmentRow> shipments) {
    }

    record DeliverCommand(String receiverName, String remarks, String otp, String signatureUrl,
                          String photoUrl) {
    }

    /**
     * @param kind {@code PHOTO} or {@code SIGNATURE} — controls only the storage key,
     *             both kinds accept the same content types
     */
    record UploadPodFileCommand(byte[] content, String filename, String contentType, String kind) {
    }

    /** One outcome per input item — {@code message} is null on success. */
    record MovementOutcome(String reference, boolean success, String message) {
    }

    /** {@code drsNumber} is only ever set by {@link #assignOutForDelivery} — null for
     *  every other bulk movement (e.g. {@link #inScan}). {@code shortageTicketNumber} is
     *  only ever set by {@link #inScan}, and only when it was called with a non-empty
     *  {@code missingTrackingNumbers} — null otherwise. */
    record BulkMovementResult(List<MovementOutcome> results, String drsNumber, String shortageTicketNumber) {
        public BulkMovementResult(List<MovementOutcome> results) {
            this(results, null, null);
        }

        public BulkMovementResult(List<MovementOutcome> results, String drsNumber) {
            this(results, drsNumber, null);
        }

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
