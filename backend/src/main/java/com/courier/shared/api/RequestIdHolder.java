package com.courier.shared.api;

/**
 * Constants for the request correlation id, shared by the servlet filter that
 * generates it, the log pattern that prints it and the response envelope that
 * returns it. Kept in one place so the three can never drift apart.
 */
public final class RequestIdHolder {

    /** MDC key; referenced by the {@code %X{requestId}} conversion in the log pattern. */
    public static final String REQUEST_ID_KEY = "requestId";

    /** MDC key for the company, also printed on every log line. */
    public static final String COMPANY_ID_KEY = "companyId";

    /** Inbound/outbound HTTP header. Honoured if the caller supplies one. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private RequestIdHolder() {
    }
}
