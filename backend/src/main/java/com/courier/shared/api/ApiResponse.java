package com.courier.shared.api;

import com.courier.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

/**
 * The single response envelope for every REST endpoint, success or failure.
 *
 * <p>A uniform shape means clients write one unwrapping/error path instead of one
 * per endpoint. Null fields are omitted, so a success response carries no error
 * keys and vice versa.
 *
 * @param success   true for 2xx outcomes
 * @param message   human-readable summary, safe to show to an end user
 * @param data      payload, null on failure
 * @param errorCode stable machine-readable code, null on success
 * @param errors    field-level validation failures, null when not applicable
 * @param timestamp server time, UTC
 * @param path      request URI, echoed for support triage
 * @param requestId correlation id — the same value appears in the server logs
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Standard response envelope")
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String errorCode,
        List<FieldError> errors,
        Instant timestamp,
        String path,
        String requestId
) {

    /** A single field-level validation failure. */
    @Schema(name = "FieldError")
    public record FieldError(String field, String message, Object rejectedValue) {
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Success");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .requestId(currentRequestId())
                .build();
    }

    /** For 201/204-style outcomes with nothing meaningful to return. */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .timestamp(Instant.now())
                .requestId(currentRequestId())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, String path) {
        return error(errorCode, message, path, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode,
                                           String message,
                                           String path,
                                           List<FieldError> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode.name())
                .errors(errors == null || errors.isEmpty() ? null : errors)
                .timestamp(Instant.now())
                .path(path)
                .requestId(currentRequestId())
                .build();
    }

    private static String currentRequestId() {
        return MDC.get(RequestIdHolder.REQUEST_ID_KEY);
    }
}
