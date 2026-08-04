package com.courier.shared.exception;

/**
 * The request is well-formed but violates a domain rule — an illegal status
 * transition, a COD amount on a prepaid shipment, a branch that still has open work.
 * Maps to 422, distinguishing it from a 400 malformed payload.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
