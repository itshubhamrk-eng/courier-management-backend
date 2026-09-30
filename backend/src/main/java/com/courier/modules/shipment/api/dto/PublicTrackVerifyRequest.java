package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * Second factor for the public tracking page's "show more" step — the last 4 digits of the
 * receiver's phone number, as booked. Proves the caller already has some legitimate
 * relationship to the shipment (they're the sender, the receiver, or were told the number
 * by one of them) before address, POD or ticket detail is returned.
 */
@Schema(name = "PublicTrackVerifyRequest")
public record PublicTrackVerifyRequest(
        @Pattern(regexp = "\\d{4}", message = "Enter exactly 4 digits")
        String phoneLast4
) {
}
