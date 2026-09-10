package com.courier.modules.ewaybill.application;

import com.courier.modules.ewaybill.application.command.CreateEwayBillCommand;
import com.courier.modules.ewaybill.application.command.EwayBillDataCommand;
import com.courier.modules.ewaybill.application.command.UpdateEwayBillCommand;
import com.courier.modules.ewaybill.domain.EwayBill;
import com.courier.modules.ewaybill.domain.EwayBillStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * E-Way Bill Management, auto-generated in two provider calls (see
 * {@code com.courier.modules.ewaybill.application.provider.EwayBillProvider}): Part-A at
 * booking time, Part-B at Manifest dispatch (vehicle assignment). Business rule this
 * module exists for: a shipment whose invoice value exceeds the company's own
 * configurable threshold ({@code CompanySettings.ewayBillMandatoryValue}, default
 * 50000.00) is mandatory; at or under it, one is optional. See {@code
 * MEMORY/modules/eway-bill.md}.
 *
 * <p><b>Three entry points create/advance a row:</b> {@link #requireBookingData}/
 * {@link #generatePartAForShipment}, called only by {@code ShipmentServiceImpl} from
 * inside the same {@code @Transactional} method that books or edits a shipment;
 * {@link #triggerPartBForShipments}, called only by {@code ManifestServiceImpl.dispatch}
 * once a vehicle is assigned; and the standalone {@link #create}/{@link #update} (a
 * company user manually attaching or amending an E-Way Bill, e.g. one obtained outside
 * this application during a provider outage).
 *
 * <p><b>Failure never blocks the caller.</b> {@link #requireBookingData} blocks booking
 * only when the mandatory <em>input data</em> (invoice number/date) is missing — that
 * predates this module's auto-generation and is unchanged. Once that gate passes, an
 * actual provider call failing (network, timeout, GSP rejection) is caught inside
 * {@link #generatePartAForShipment}/{@link #triggerPartBForShipments} and persisted as
 * {@code FAILED}; it is never rethrown, so it can never roll back a shipment booking or
 * a manifest dispatch. {@link #retry} is the one place a provider failure legitimately
 * throws — it is a direct, standalone user action, not embedded in an unrelated
 * transaction, and the user needs to see the result immediately.
 */
public interface EwayBillService {

    /** The invoice-value threshold in effect for the caller's company right now. */
    BigDecimal mandatoryThreshold();

    /** {@code invoiceValue > mandatoryThreshold()} — never hardcoded at any call site. */
    boolean isRequired(BigDecimal invoiceValue);

    /**
     * Called by {@code ShipmentServiceImpl.create}/{@code update} before the shipment is
     * persisted. No-op when {@code invoiceValue} does not exceed the threshold.
     * Otherwise requires {@code ewayBill} to carry at least an invoice number and date —
     * the minimum a Part-A request needs. Never calls the provider: a provider outcome
     * must never gate whether the shipment itself may be booked, only whether the data
     * to attempt one exists at all.
     *
     * @throws com.courier.shared.exception.BusinessRuleException exactly "E-Way Bill is
     *         mandatory because invoice value exceeds ₹50,000." (rendered with the actual
     *         threshold) when the minimum data is missing
     */
    void requireBookingData(BigDecimal invoiceValue, EwayBillDataCommand ewayBill);

    /**
     * Called by {@code ShipmentServiceImpl.create}/{@code update} after the shipment has
     * an id and {@link #requireBookingData} has already passed, whenever the shipment's
     * E-Way Bill is mandatory. Idempotent by (shipment, invoice number): a retry of the
     * exact same booking call (e.g. a client retry after a network blip) finds the row
     * it already created and, if Part-A already succeeded, does not call the provider
     * again. Any provider failure is caught and persisted as {@code FAILED} — this
     * method itself never throws for a provider-side reason.
     */
    EwayBill generatePartAForShipment(UUID shipmentId, EwayBillDataCommand ewayBill, ShipmentEwayBillContext context);

    /**
     * The optional manual-attach path: called by {@code ShipmentServiceImpl.create}/
     * {@code update} when {@code ewayBill} is supplied but the shipment's own invoice
     * value does not make one mandatory. No provider call — this is for recording an
     * E-Way Bill the operator already holds (a number typed in {@code ewayBill
     * .ewayBillNumber()} marks the row {@code GENERATED} directly; otherwise it is left
     * {@code PART_A_PENDING} for a later manual edit). No-op when {@code ewayBill} is
     * null. Finds the shipment's current row and updates it in place, or creates a
     * fresh one — never appends a second live row for the same booking call.
     */
    EwayBill upsertForShipment(UUID shipmentId, EwayBillDataCommand ewayBill);

    /** The shipment's current E-Way Bill (newest non-cancelled row, or simply the newest
     *  if every row is cancelled), for Shipment Booking's own detail response. Empty when
     *  the shipment has never had one. */
    Optional<EwayBillSnapshot> findLatestForShipment(UUID shipmentId);

    /** Batch form of {@link #findLatestForShipment} — one query for a whole page of
     *  shipments (a THC's shipment list, a Shipment list page) rather than one per row.
     *  A shipment missing from the returned map has never had an E-Way Bill. */
    Map<UUID, EwayBillSnapshot> findLatestForShipments(Collection<UUID> shipmentIds);

    /**
     * Called by {@code ManifestServiceImpl.dispatch} once the vehicle/driver are
     * assigned, for every shipment on the manifest. Shipments with no E-Way Bill, or
     * one not yet past Part-A, are silently skipped — there is nothing to complete
     * yet. Never throws: one shipment's provider failure must not stop the others, and
     * dispatch itself must never roll back because of it.
     */
    void triggerPartBForShipments(Collection<UUID> shipmentIds, String vehicleNumber,
                                  String transporterId, String transportMode);

    // ------------------------------------------------------------- standalone lifecycle

    EwayBill create(CreateEwayBillCommand command);

    EwayBill update(UUID id, UpdateEwayBillCommand command);

    EwayBill getById(UUID id);

    Page<EwayBill> search(UUID shipmentId, EwayBillStatus status, Pageable pageable);

    /**
     * Re-attempts whichever stage last failed: Part-A when the row has no
     * provider-issued number yet (never succeeded, or {@code EXPIRED}), Part-B
     * otherwise. Only legal from {@code FAILED}/{@code EXPIRED} — unlike the
     * booking-flow/dispatch-flow calls above, this is a direct user action and throws
     * on a provider failure so the user sees it immediately.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the row is not
     *         {@code FAILED}/{@code EXPIRED}, or the provider call itself fails
     */
    EwayBill retry(UUID id);

    /**
     * Stores the E-Way Bill document (PDF/JPG/PNG) via the same {@code FileStoragePort}
     * Shipment Booking already uses — no second file-storage seam. Purely a document
     * attachment; it never changes the row's status.
     */
    String upload(UUID id, UploadCommand command);

    /**
     * Cancels an already-issued E-Way Bill with the provider first (when the row has a
     * provider-issued number), then marks the row {@code CANCELLED} — a row that never
     * got past Part-A has nothing to cancel remotely and is cancelled locally only.
     *
     * @throws com.courier.shared.exception.BusinessRuleException already cancelled, or
     *         the provider refuses/cannot be reached for the remote cancellation
     */
    EwayBill cancel(UUID id, String remarks);

    record UploadCommand(byte[] content, String filename, String contentType) {
    }

    /**
     * Everything {@link #generatePartAForShipment} needs from the shipment itself that
     * is not already user-typed on {@link EwayBillDataCommand} — sender/receiver
     * identity and a short product description, all readily available on an
     * already-built {@code Shipment}/its items. Kept separate from
     * {@code EwayBillDataCommand} because this module has no business accepting these
     * as freeform booking-screen input; they come from the shipment record itself.
     */
    record ShipmentEwayBillContext(
            String consignorName, String consignorAddress, String consignorPincode,
            String consigneeName, String consigneeAddress, String consigneePincode,
            String productDescription) {
    }

    /** Read-only projection Shipment Booking's own response embeds — deliberately not the
     *  {@link EwayBill} entity itself, so {@code shipment.api} never depends on this
     *  module's domain package (only its application interface). */
    record EwayBillSnapshot(UUID id, String ewayBillNumber, String status, String invoiceNumber,
                            BigDecimal invoiceValue, Instant validFrom, Instant validUntil,
                            String documentUrl, String lastError) {
    }
}
