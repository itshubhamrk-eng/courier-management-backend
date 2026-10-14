package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the admin-side Track view needs to offer, preview and flag a PAID / TO_PAY switch. */
@Schema(name = "PaymentModeChangeResponse")
public record PaymentModeChangeResponse(
        UUID currentModeId, String currentModeName,
        @Schema(description = "True while the shipment is BOOKED and has a switchable counterpart mode")
        boolean changeable,
        @Schema(description = "The shipment's current net amount, i.e. what a switch to PAID debits "
                + "or a switch from PAID refunds") BigDecimal currentNetAmount,
        @Schema(description = "Booking branch wallet's available balance") BigDecimal walletBalance,
        boolean changed, String changedFromModeName, Instant changedAt,
        List<Target> targets
) {
    /** A mode the shipment can be switched to. {@code netAmount} is the new net after the
     *  switch (To-Pay charge added or removed); {@code direction} is {@code DEBIT} (to a
     *  collect-at-booking mode) or {@code CREDIT} (back to collect-at-delivery). */
    public record Target(UUID id, String name, String direction, BigDecimal netAmount,
                         BigDecimal walletAmount, boolean sufficientBalance) {
    }
}
