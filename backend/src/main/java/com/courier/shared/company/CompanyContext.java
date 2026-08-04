package com.courier.shared.company;

import com.courier.shared.exception.CompanyIsolationException;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the company of the current request.
 *
 * <p>Deliberately a plain {@link ThreadLocal} and <b>not</b> an
 * {@code InheritableThreadLocal}: inheriting the company into arbitrary child
 * threads is exactly how a background task ends up writing to the wrong company.
 * Code that hands work to another thread must pass the company id explicitly and
 * call {@link #runAs} on the receiving side.
 *
 * <p>{@link #clear()} must run in a {@code finally} block on every entry point.
 * Servlet threads (and virtual threads under a pooled carrier) are reused, so a
 * leaked value would be inherited by the next unrelated request.
 */
@Slf4j
public final class CompanyContext {

    private static final ThreadLocal<UUID> CURRENT_COMPANY = new ThreadLocal<>();

    private CompanyContext() {
    }

    public static void setCompanyId(UUID companyId) {
        if (companyId == null) {
            clear();
            return;
        }
        log.trace("Binding company {} to current thread", companyId);
        CURRENT_COMPANY.set(companyId);
    }

    /**
     * @return the current company, or empty for unauthenticated / platform-level work
     */
    public static Optional<UUID> getCompanyId() {
        return Optional.ofNullable(CURRENT_COMPANY.get());
    }

    /**
     * @throws CompanyIsolationException if no company is bound — use this in code
     *         that has no meaning outside a company, so the failure is loud rather
     *         than a silent query across all companies
     */
    public static UUID requireCompanyId() {
        UUID companyId = CURRENT_COMPANY.get();
        if (companyId == null) {
            throw new CompanyIsolationException(
                    "No company bound to the current request; a company-scoped operation was attempted");
        }
        return companyId;
    }

    public static boolean isSet() {
        return CURRENT_COMPANY.get() != null;
    }

    public static void clear() {
        CURRENT_COMPANY.remove();
    }

    /**
     * Runs an action bound to the given company, restoring the previous binding
     * afterwards. The only supported way to switch companies — for scheduled jobs,
     * async workers and platform-admin operations.
     */
    public static <T> T runAs(UUID companyId, java.util.function.Supplier<T> action) {
        UUID previous = CURRENT_COMPANY.get();
        try {
            setCompanyId(companyId);
            return action.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                CURRENT_COMPANY.set(previous);
            }
        }
    }

    public static void runAs(UUID companyId, Runnable action) {
        runAs(companyId, () -> {
            action.run();
            return null;
        });
    }
}
