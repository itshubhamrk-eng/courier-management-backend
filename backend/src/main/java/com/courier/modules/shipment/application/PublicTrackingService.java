package com.courier.modules.shipment.application;

import com.courier.modules.shipment.api.dto.PublicShipmentTicketRequest;
import com.courier.modules.shipment.api.dto.PublicShipmentTicketResponse;
import com.courier.modules.shipment.api.dto.PublicTrackDetailResponse;
import com.courier.modules.shipment.api.dto.PublicTrackResponse;

/**
 * Track Shipment for an unauthenticated caller (the login page's "Track Shipment" link,
 * and any other public tracking page). Deliberately separate from {@link ShipmentService}:
 * every method there is gated by {@code @PreAuthorize} on an authenticated company user,
 * and this is the one lookup in the module that must work without either — keeping it in
 * its own class makes that boundary a file, not a missing annotation to notice.
 */
public interface PublicTrackingService {

    /** Looked up cross-company by tracking or shipment number. */
    PublicTrackResponse track(String number);

    /**
     * The extended, PII-bearing detail view — sender/receiver, current location, POD,
     * tickets — unlocked only once {@code phoneLast4} matches the last 4 digits of the
     * receiver's phone on file. See {@link PublicTrackVerificationLimiter} for the
     * brute-force throttling this relies on.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the code does not match,
     *         or this shipment/number has hit the attempt limit
     */
    PublicTrackDetailResponse verifyAndGetDetail(String number, String phoneLast4);

    /**
     * "Raise a Complaint/Query" against this shipment, from the public tracking page.
     * Same second-factor gate as {@link #verifyAndGetDetail} — re-checked independently,
     * not trusted from an earlier call — and the same rate limiter/key, so a caller can't
     * use ticket-raising as a second avenue to brute-force the phone code.
     *
     * @throws com.courier.shared.exception.BusinessRuleException the code does not match,
     *         or this shipment/number has hit the attempt limit
     */
    PublicShipmentTicketResponse raiseTicket(String number, PublicShipmentTicketRequest request);
}
