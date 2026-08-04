package com.courier.shared.exception;

/**
 * A uniqueness constraint would be violated — a taken company slug, a branch code
 * already used within the company, a duplicate AWB.
 */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }

    public DuplicateResourceException(String resource, String field, Object value) {
        super(ErrorCode.DUPLICATE_RESOURCE,
                "%s with %s '%s' already exists".formatted(resource, field, value));
    }
}
