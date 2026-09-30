package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The extended, PII-bearing projection unlocked by {@code PublicTrackingService
 * #verifyAndGetDetail} — sender/receiver name, address and phone, current hub location,
 * proof of delivery and any customer-care tickets. Everything {@link PublicTrackResponse}
 * omits, now that the caller has proven (via {@link PublicTrackVerifyRequest}) they already
 * know the receiver's phone number rather than just guessing a sequential tracking number.
 */
@Schema(name = "PublicTrackDetailResponse", description = "Verified detail projection for public tracking")
public record PublicTrackDetailResponse(
        String senderName,
        String senderAddress,
        String senderContact,
        String receiverName,
        String receiverAddress,
        String receiverContact,
        String currentLocation,
        PublicTrackPodResponse pod,
        List<PublicTrackTicketResponse> tickets
) {
}
