package com.courier.shared.exception;

/**
 * A requested resource does not exist, or is not visible to the current company.
 *
 * <p>Note the deliberate ambiguity: for a company-owned resource the filter makes
 * another company's row simply invisible, so we return 404 rather than 403. Telling
 * a caller "this exists but is not yours" leaks the existence of other companies'
 * data.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException(String resource, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s not found: %s".formatted(resource, identifier));
    }
}
