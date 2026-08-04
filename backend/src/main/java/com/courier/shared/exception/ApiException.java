package com.courier.shared.exception;

import lombok.Getter;

/**
 * Base class for every exception the application raises deliberately.
 *
 * <p>Carries an {@link ErrorCode}, which determines both the HTTP status and the
 * machine-readable code in the response envelope. Anything not extending this class
 * that reaches the handler is treated as an unexpected 500 and logged with a stack
 * trace.
 *
 * <p>No stack trace is filled in ({@code writableStackTrace = false}): these are
 * expected control-flow signals thrown on hot paths such as validation, and
 * capturing a trace for each one is pure overhead. Genuine faults still get traces.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final transient ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode, String message) {
        super(message, null, false, false);
        this.errorCode = errorCode;
    }

    protected ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }
}
