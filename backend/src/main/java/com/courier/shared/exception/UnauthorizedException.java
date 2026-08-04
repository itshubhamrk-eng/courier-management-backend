package com.courier.shared.exception;

/**
 * Authentication failed or was absent. Messages must stay generic — never reveal
 * whether an account exists, which is why credential failures all use
 * {@link ErrorCode#INVALID_CREDENTIALS}.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
