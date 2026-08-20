package com.courier.modules.ewaybill.application.provider;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Local, offline validation only — field format and internal consistency, never a call to
 * the government e-way bill portal. "Do not implement an external government API unless
 * already configured" (this deployment has none configured); when one is, it becomes a
 * second {@link EwayBillProvider} implementation swapped in by configuration, with no
 * change to {@code EwayBillServiceImpl} or Shipment Booking.
 *
 * <p>The 12-digit format checked below is the real GST e-way bill number shape — this
 * provider checks the shape, not that the number was actually issued.
 */
@Component
public class LocalEwayBillProvider implements EwayBillProvider {

    private static final Pattern EWAY_BILL_NUMBER = Pattern.compile("^\\d{12}$");

    @Override
    public ValidationOutcome validate(ValidationRequest request) {
        if (request.ewayBillNumber() == null || request.ewayBillNumber().isBlank()) {
            return ValidationOutcome.invalid("An E-Way Bill number is required.");
        }
        if (!EWAY_BILL_NUMBER.matcher(request.ewayBillNumber().trim()).matches()) {
            return ValidationOutcome.invalid("E-Way Bill number must be exactly 12 digits.");
        }
        if (request.invoiceNumber() == null || request.invoiceNumber().isBlank()) {
            return ValidationOutcome.invalid("An invoice number is required.");
        }
        if (request.invoiceDate() == null) {
            return ValidationOutcome.invalid("An invoice date is required.");
        }
        if (request.invoiceValue() == null || request.invoiceValue().signum() <= 0) {
            return ValidationOutcome.invalid("Invoice value must be greater than zero.");
        }
        if (request.validUntil() != null && request.validUntil().isBefore(Instant.now())) {
            return ValidationOutcome.invalid("E-Way Bill validity has already expired.");
        }
        if (request.validFrom() != null && request.validUntil() != null
                && request.validUntil().isBefore(request.validFrom())) {
            return ValidationOutcome.invalid("E-Way Bill validity cannot end before it starts.");
        }
        return ValidationOutcome.ok();
    }
}
