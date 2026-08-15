package com.courier.shared.exception;

/**
 * A {@link BusinessRuleException} specific to "no route runs this lane", "the route is
 * inactive", "no active rate covers this combination" or "no weight slab covers this
 * weight" — the cases Shipment Booking treats as a signal to fall back to the company's
 * distance+weight Freight Factor pricing instead of failing the booking outright. Still
 * maps to 422 like any other {@link BusinessRuleException}; the subtype only lets callers
 * that care distinguish it from an unrelated business-rule refusal (serviceability,
 * declared value, wallet balance, etc).
 */
public class RouteRateUnavailableException extends BusinessRuleException {

    public RouteRateUnavailableException(String message) {
        super(message);
    }
}
