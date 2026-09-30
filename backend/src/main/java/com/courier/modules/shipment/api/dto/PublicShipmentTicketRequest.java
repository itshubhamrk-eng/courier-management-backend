package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * "Raise a Complaint/Query" on the public tracking page's verified detail view. Same second
 * factor as {@link PublicTrackVerifyRequest} — re-checked independently here too, since
 * there is no session between calls to trust that an earlier {@code /verify} succeeded.
 */
@Schema(name = "PublicShipmentTicketRequest")
public record PublicShipmentTicketRequest(
        @Pattern(regexp = "\\d{4}", message = "Enter exactly 4 digits") String phoneLast4,
        @NotBlank(message = "Subject is required") @Size(max = 150) String subject,
        @NotBlank(message = "Message is required") @Size(max = 2000) String message
) {
}
