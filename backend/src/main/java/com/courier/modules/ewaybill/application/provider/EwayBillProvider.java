package com.courier.modules.ewaybill.application.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The seam between "raise/amend an E-Way Bill" and whichever government/GSP network
 * actually does it — today that is {@code UnconfiguredEwayBillProvider} (no production
 * credentials exist in this deployment); a real GSP integration is a second
 * implementation of this interface selected by configuration (see
 * {@code EwayBillProviderConfig}), with no change to {@code EwayBillServiceImpl} or
 * Shipment Booking/Manifest dispatch. The same split {@code FileStoragePort} draws for
 * object storage and {@code PaymentGatewayPort} draws for Razorpay.
 *
 * <p>Every operation returns an outcome record rather than throwing for an ordinary
 * business failure (not configured, rejected, timed out) — {@code EwayBillServiceImpl}
 * persists that outcome as {@code FAILED} and moves on; it must never let a provider
 * failure roll back a shipment booking or a manifest dispatch. An implementation may
 * still let an unexpected {@link RuntimeException} escape for a truly exceptional
 * failure (e.g. a bug in request construction) — callers catch defensively regardless.
 */
public interface EwayBillProvider {

    /** Raises Part-A: invoice + consignor/consignee. Called once per shipment (by
     *  invoice number) at booking time, and again on {@code retry} when Part-A itself
     *  never succeeded or the row expired. */
    PartAResult generatePartA(PartARequest request);

    /** Adds/replaces the transport details on an already-issued E-Way Bill. Called at
     *  Manifest dispatch (vehicle assignment) and again on {@code retry} when Part-A
     *  already succeeded but Part-B did not. */
    PartBResult updatePartB(PartBRequest request);

    /** Polls the provider's own current status for an already-issued number — used to
     *  detect e.g. an out-of-band cancellation or expiry. Never mutates anything itself. */
    StatusResult getStatus(String ewayBillNumber);

    /** Withdraws an already-issued E-Way Bill. A row that never got past Part-A (no
     *  number yet) has nothing to cancel remotely — {@code EwayBillServiceImpl} skips
     *  calling this in that case. */
    CancelResult cancel(String ewayBillNumber, String reason);

    // ------------------------------------------------------------------------ Part-A

    record PartARequest(
            String invoiceNumber, LocalDate invoiceDate, BigDecimal invoiceValue,
            String documentType, String documentNumber, LocalDate documentDate,
            String consignorGstin, String consignorName, String consignorAddress, String consignorPincode,
            String consigneeGstin, String consigneeName, String consigneeAddress, String consigneePincode,
            String productDescription) {
    }

    record PartAResult(boolean success, String ewayBillNumber, Instant validFrom, Instant validUntil,
                       String providerName, String providerReference, String failureReason) {
        public static PartAResult success(String ewayBillNumber, Instant validFrom, Instant validUntil,
                                          String providerName, String providerReference) {
            return new PartAResult(true, ewayBillNumber, validFrom, validUntil, providerName, providerReference, null);
        }

        public static PartAResult failure(String providerName, String reason) {
            return new PartAResult(false, null, null, null, providerName, null, reason);
        }
    }

    // ------------------------------------------------------------------------ Part-B

    record PartBRequest(String ewayBillNumber, String vehicleNumber, String transporterId, String transportMode) {
    }

    record PartBResult(boolean success, String providerReference, String failureReason) {
        public static PartBResult success(String providerReference) {
            return new PartBResult(true, providerReference, null);
        }

        public static PartBResult failure(String reason) {
            return new PartBResult(false, null, reason);
        }
    }

    // ------------------------------------------------------------------------ status / cancel

    record StatusResult(boolean found, String remoteStatus, String detail) {
        public static StatusResult notFound() {
            return new StatusResult(false, null, null);
        }
    }

    record CancelResult(boolean success, String failureReason) {
        public static CancelResult ok() {
            return new CancelResult(true, null);
        }

        public static CancelResult failure(String reason) {
            return new CancelResult(false, reason);
        }
    }
}
