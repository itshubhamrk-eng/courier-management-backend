package com.courier.shared.exception;

import com.courier.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Translates every exception into the standard {@link ApiResponse} envelope.
 *
 * <p>Two rules govern this class:
 * <ol>
 *   <li><b>Nothing internal escapes.</b> Stack traces, SQL, constraint names and
 *       class names never reach the client. They go to the log, keyed by the
 *       request id that <i>is</i> returned, so support can join the two.</li>
 *   <li><b>Log level matches blame.</b> 4xx is the caller's problem — WARN, no
 *       trace. 5xx is ours — ERROR with the full trace.</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------------------------------------------------------------- application

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();

        if (code == ErrorCode.COMPANY_ISOLATION_VIOLATION) {
            // Never routine. Either a bug in isolation or a deliberate probe.
            log.error("COMPANY ISOLATION VIOLATION at {} {}: {}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
        } else {
            log.warn("{} at {} {}: {}", code, request.getMethod(), request.getRequestURI(), ex.getMessage());
        }

        return build(code, ex.getMessage(), request, null);
    }

    // ----------------------------------------------------------------- validation

    /** Bean-validation failures on {@code @Valid @RequestBody} arguments. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<ApiResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();

        log.warn("Validation failed at {} {}: {} field error(s)",
                request.getMethod(), request.getRequestURI(), errors.size());

        return build(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, errors);
    }

    /** Bean-validation failures on {@code @Validated} method parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex,
                                                                       HttpServletRequest request) {
        List<ApiResponse.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiResponse.FieldError(
                        v.getPropertyPath().toString(), v.getMessage(), v.getInvalidValue()))
                .toList();

        log.warn("Constraint violation at {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.VALIDATION_FAILED, "Request validation failed", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        // The parser message can quote the payload, which may contain credentials.
        log.warn("Malformed request body at {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.MALFORMED_REQUEST, "Request body is missing or malformed", request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        return build(ErrorCode.MISSING_PARAMETER,
                "Required parameter '%s' is missing".formatted(ex.getParameterName()), request, null);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(
            org.springframework.web.multipart.support.MissingServletRequestPartException ex,
            HttpServletRequest request) {
        return build(ErrorCode.MISSING_PARAMETER,
                "Required file part '%s' is missing".formatted(ex.getRequestPartName()), request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                HttpServletRequest request) {
        return build(ErrorCode.TYPE_MISMATCH,
                "Parameter '%s' has an invalid value".formatted(ex.getName()), request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                                  HttpServletRequest request) {
        log.warn("Upload rejected, too large at {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getDefaultMessage(), request, null);
    }

    // ------------------------------------------------------------------- security

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex,
                                                                  HttpServletRequest request) {
        log.warn("Authentication failed at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        // Generic message: never disclose whether the account exists.
        return build(ErrorCode.UNAUTHENTICATED, "Authentication required", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex,
                                                                HttpServletRequest request) {
        log.warn("Access denied at {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.getDefaultMessage(), request, null);
    }

    // ---------------------------------------------------------------- persistence

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                 HttpServletRequest request) {
        // Root cause names the constraint and often the offending value — log only.
        log.error("Data integrity violation at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.DUPLICATE_RESOURCE,
                "The operation conflicts with existing data", request, null);
    }

    /**
     * Covers both flavors of "two requests fought over the same row and one lost":
     * {@code OptimisticLockingFailureException} (a {@code @Version} mismatch) and
     * {@code PessimisticLockingFailureException}/{@code CannotAcquireLockException}
     * (a real InnoDB deadlock or lock-wait timeout) — both are
     * {@link ConcurrencyFailureException}. Before this was narrowed to only the
     * optimistic case, a genuine deadlock fell through to the generic 500 handler
     * instead of the same retryable 409 (found running a k6 load test: concurrent
     * logins to the same account deadlocking on the users row update).
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrencyFailure(ConcurrencyFailureException ex,
                                                                       HttpServletRequest request) {
        log.warn("Concurrency conflict at {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.CONCURRENT_MODIFICATION,
                "This record was modified by someone else. Reload and try again.", request, null);
    }

    // ---------------------------------------------------------------------- HTTP

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                      HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED,
                "Method %s is not supported for this endpoint".formatted(ex.getMethod()), request, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex,
                                                             HttpServletRequest request) {
        return build(ErrorCode.ENDPOINT_NOT_FOUND, "Endpoint not found", request, null);
    }

    /** Thrown instead of {@link NoHandlerFoundException} since Spring Boot 3.2 for unmatched routes. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex,
                                                               HttpServletRequest request) {
        return build(ErrorCode.ENDPOINT_NOT_FOUND, "Endpoint not found", request, null);
    }

    // ------------------------------------------------------------------- fallback

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}", request.getMethod(), request.getRequestURI(), ex);
        // Deliberately opaque. The request id in the envelope is how support finds
        // the log line above.
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request, null);
    }

    // -------------------------------------------------------------------- helpers

    private static ResponseEntity<ApiResponse<Void>> build(ErrorCode code,
                                                           String message,
                                                           HttpServletRequest request,
                                                           List<ApiResponse.FieldError> errors) {
        HttpStatus status = code.getStatus();
        String safeMessage = (message == null || message.isBlank()) ? code.getDefaultMessage() : message;
        return ResponseEntity.status(status)
                .body(ApiResponse.error(code, safeMessage, request.getRequestURI(), errors));
    }

    private static ApiResponse.FieldError toFieldError(FieldError fieldError) {
        Object rejected = fieldError.getRejectedValue();
        // Never echo a submitted password back to the caller or into a log.
        boolean sensitive = fieldError.getField().toLowerCase().contains("password");
        return new ApiResponse.FieldError(
                fieldError.getField(),
                fieldError.getDefaultMessage(),
                sensitive ? null : rejected);
    }
}
