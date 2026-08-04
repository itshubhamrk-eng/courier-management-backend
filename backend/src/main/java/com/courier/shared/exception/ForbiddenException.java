package com.courier.shared.exception;

/**
 * The caller is authenticated but lacks permission for this action.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
