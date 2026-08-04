package com.courier.shared.exception;

/**
 * A company isolation invariant was broken: no company bound where one is required,
 * or an attempt to touch another company's row.
 *
 * <p>This is never an expected condition. Every occurrence is a bug or an attack and
 * must be logged at ERROR and alerted on — see {@code MEMORY/PROJECT.md}, where a
 * cross-company access is classified Sev-1.
 */
public class CompanyIsolationException extends ApiException {

    public CompanyIsolationException(String message) {
        super(ErrorCode.COMPANY_ISOLATION_VIOLATION, message);
    }
}
