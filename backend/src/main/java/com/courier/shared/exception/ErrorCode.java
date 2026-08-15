package com.courier.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in {@code ApiResponse.errorCode}.
 *
 * <p>Clients branch on these strings, so a code's meaning must never change once
 * released — add a new constant instead of repurposing an old one. The HTTP status
 * is derived from the code so that a given failure always maps to the same status.
 */
@Getter
public enum ErrorCode {

    // 400
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request body"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Required parameter is missing"),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "Parameter type mismatch"),

    // 401
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token is invalid"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or has been used"),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Token has been revoked"),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Session has expired"),

    // 403
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    COMPANY_INACTIVE(HttpStatus.FORBIDDEN, "Company is not active"),
    COMPANY_ISOLATION_VIOLATION(HttpStatus.FORBIDDEN, "Cross-company access is not permitted"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account is disabled"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email address has not been verified"),
    PASSWORD_EXPIRED(HttpStatus.FORBIDDEN, "Password has expired and must be changed"),

    // 404
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "Endpoint not found"),

    // 405 / 409 / 413 / 415
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Resource was modified by another request"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed size"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),

    // 422 — syntactically valid, semantically rejected
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation"),
    INVALID_STATE_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid state transition"),
    WEAK_PASSWORD(HttpStatus.UNPROCESSABLE_ENTITY, "Password does not meet the security policy"),

    // 423 / 429
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account is locked"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // 5xx
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
