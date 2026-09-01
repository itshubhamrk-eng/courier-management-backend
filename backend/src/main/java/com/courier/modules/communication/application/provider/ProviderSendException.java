package com.courier.modules.communication.application.provider;

/** A provider call failed — network error, vendor 4xx/5xx, missing/invalid credentials.
 *  Caught by {@code CommunicationSendService}, which marks the log row {@code FAILED} and
 *  schedules a retry; never propagates to the caller of the original shipment action —
 *  see {@code ShipmentCommunicationListener}'s own doc for why a notification failure can
 *  never roll back a shipment operation. */
public class ProviderSendException extends RuntimeException {

    public ProviderSendException(String message) {
        super(message);
    }

    public ProviderSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
