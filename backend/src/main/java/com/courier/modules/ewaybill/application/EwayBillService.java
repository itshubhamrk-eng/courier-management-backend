package com.courier.modules.ewaybill.application;

import com.courier.modules.ewaybill.application.command.CreateEwayBillCommand;
import com.courier.modules.ewaybill.application.command.EwayBillDataCommand;
import com.courier.modules.ewaybill.application.command.UpdateEwayBillCommand;
import com.courier.modules.ewaybill.domain.EwayBill;
import com.courier.modules.ewaybill.domain.EwayBillStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * E-Way Bill Management. Business rule this module exists for: a shipment whose invoice
 * value exceeds the company's own configurable threshold
 * ({@code CompanySettings.ewayBillMandatoryValue}, default 50000.00) may not have its AWB
 * generated until it carries a {@code VALIDATED} E-Way Bill; at or under it, one is
 * optional. See {@code MEMORY/modules/eway-bill.md}.
 *
 * <p><b>Two entry points create/update a row:</b> the standalone {@link #create}/
 * {@link #update} (a company user attaching or amending an E-Way Bill against an
 * already-booked shipment), and {@link #enforceBookingRequirement}/{@link
 * #upsertForShipment}, called only by {@code ShipmentServiceImpl} from inside the same
 * {@code @Transactional} method that books or edits a shipment — so a mandatory-but-invalid
 * E-Way Bill blocks AWB generation by rolling back the whole booking, not by a separate
 * pre-check a caller could skip. Backend-enforced either way; nothing here trusts the
 * frontend to have already decided a booking is allowed.
 */
public interface EwayBillService {

    /** The invoice-value threshold in effect for the caller's company right now. */
    BigDecimal mandatoryThreshold();

    /** {@code invoiceValue > mandatoryThreshold()} — never hardcoded at any call site. */
    boolean isRequired(BigDecimal invoiceValue);

    /**
     * Called by {@code ShipmentServiceImpl.create}/{@code update} before the shipment is
     * persisted. No-op when {@code invoiceValue} does not exceed the threshold. Otherwise
     * requires {@code ewayBill} to be present and to pass {@code EwayBillProvider}
     * validation.
     *
     * @throws com.courier.shared.exception.BusinessRuleException exactly "E-Way Bill is
     *         mandatory because invoice value exceeds ₹50,000." (rendered with the actual
     *         threshold) when missing, or the provider's own reason when present but invalid
     */
    void enforceBookingRequirement(BigDecimal invoiceValue, EwayBillDataCommand ewayBill);

    /**
     * Called by {@code ShipmentServiceImpl.create}/{@code update} after the shipment has an
     * id and {@link #enforceBookingRequirement} has already passed. No-op when
     * {@code ewayBill} is null (nothing to attach — legal whenever an E-Way Bill is
     * optional). Finds the shipment's current row and updates it in place, or creates a
     * fresh one — never appends a second live row for the same booking call.
     */
    EwayBill upsertForShipment(UUID shipmentId, EwayBillDataCommand ewayBill);

    /** The shipment's current E-Way Bill (newest non-cancelled row, or simply the newest
     *  if every row is cancelled), for Shipment Booking's own detail response. Empty when
     *  the shipment has never had one. */
    Optional<EwayBillSnapshot> findLatestForShipment(UUID shipmentId);

    // ------------------------------------------------------------- standalone lifecycle

    EwayBill create(CreateEwayBillCommand command);

    EwayBill update(UUID id, UpdateEwayBillCommand command);

    EwayBill getById(UUID id);

    Page<EwayBill> search(UUID shipmentId, EwayBillStatus status, Pageable pageable);

    /**
     * Re-runs {@code EwayBillProvider} validation against the row's current fields and
     * transitions it to {@code VALIDATED} or {@code INVALID} accordingly.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the row is {@code CANCELLED}
     */
    EwayBill validate(UUID id);

    /**
     * Stores the E-Way Bill document (PDF/JPG/PNG) via the same {@code FileStoragePort}
     * Shipment Booking already uses — no second file-storage seam. Moves a
     * {@code PENDING}/{@code REQUIRED}/{@code NOT_REQUIRED}/{@code INVALID} row to
     * {@code UPLOADED}; a row already {@code VALIDATED} keeps its status (a document can be
     * replaced without undoing an already-confirmed validation).
     */
    String upload(UUID id, UploadCommand command);

    EwayBill cancel(UUID id, String remarks);

    record UploadCommand(byte[] content, String filename, String contentType) {
    }

    /** Read-only projection Shipment Booking's own response embeds — deliberately not the
     *  {@link EwayBill} entity itself, so {@code shipment.api} never depends on this
     *  module's domain package (only its application interface). */
    record EwayBillSnapshot(UUID id, String ewayBillNumber, String status, BigDecimal invoiceValue,
                            java.time.Instant validFrom, java.time.Instant validUntil,
                            String documentUrl) {
    }
}
