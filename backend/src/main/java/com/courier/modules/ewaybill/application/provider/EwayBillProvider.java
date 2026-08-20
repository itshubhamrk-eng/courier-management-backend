package com.courier.modules.ewaybill.application.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The seam between "validate an E-Way Bill" and whichever authority actually confirms one
 * — today that is this module's own field/format sanity checks; a future government/GST
 * network API integration is a new implementation of this interface and a config change,
 * the same split {@code FileStoragePort} draws for object storage and
 * {@code PaymentGatewayPort} draws for Razorpay. Nothing in {@code EwayBillServiceImpl} or
 * Shipment Booking knows which implementation is active.
 *
 * <p>No external government API is wired up in this deployment — see
 * {@code LocalEwayBillProvider}, the only implementation today.
 */
public interface EwayBillProvider {

    ValidationOutcome validate(ValidationRequest request);

    record ValidationRequest(String ewayBillNumber, String invoiceNumber, LocalDate invoiceDate,
                             BigDecimal invoiceValue, String vehicleNumber,
                             Instant validFrom, Instant validUntil) {
    }

    record ValidationOutcome(boolean valid, String reason) {
        public static ValidationOutcome ok() {
            return new ValidationOutcome(true, null);
        }

        public static ValidationOutcome invalid(String reason) {
            return new ValidationOutcome(false, reason);
        }
    }
}
