package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/master/payment-modes}.
 *
 * <p>The flags, not the code, are what the shipment and wallet modules branch on. The four
 * canonical combinations: PAID collects at booking; TO_PAY at delivery; TBB requires a
 * credit account and collects nothing; COD collects at delivery and is cash on delivery.
 * Contradictions — collecting at both ends, or a billed mode that also takes cash — are
 * refused with 422.
 */
@Schema(name = "CreatePaymentModeRequest", description = "New payment mode")
public record CreatePaymentModeRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_ -]{0,48}[A-Za-z0-9]$",
                message = "2-50 chars of letters, digits, space, hyphen or underscore")
        @Schema(example = "TO_PAY") String code,
        @NotBlank @Size(max = 150) @Schema(example = "To Pay") String name,
        @Size(max = 500) String description,
        @PositiveOrZero Integer displayOrder,
        Boolean collectAtBooking,
        Boolean collectAtDelivery,
        Boolean requiresCreditAccount,
        Boolean cashOnDelivery
) {
}
