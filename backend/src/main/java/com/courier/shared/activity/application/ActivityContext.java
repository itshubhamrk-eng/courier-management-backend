package com.courier.shared.activity.application;

import java.util.Map;

/**
 * Optional per-request enrichment for {@code ActivityLoggingFilter}'s automatic capture.
 *
 * <p>The filter already logs every authenticated API call with no code change anywhere —
 * that is the point of requirement 4 (automatic logging). This context exists purely for
 * the handful of call sites that know something the URL alone cannot express: a shipment's
 * status changed from {@code BOOKED} to {@code DISPATCHED}, a role's permission set before
 * and after. A service calls {@link #recordChange} once, inline with the write it is
 * already making; the filter picks the values up after the response is produced and
 * clears them before the thread is returned to the pool.
 *
 * <p>Plain {@link ThreadLocal}, not inheritable, for the same reason {@code CompanyContext}
 * is not: this is a servlet-thread-scoped enrichment, never meant to cross into a
 * background task.
 */
public final class ActivityContext {

    private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();

    private ActivityContext() {
    }

    public record Entry(String module,
                        String submodule,
                        String description,
                        String entityType,
                        String entityId,
                        Map<String, Object> oldValue,
                        Map<String, Object> newValue) {
    }

    /**
     * Records what changed for the current request. Called by a service right next to the
     * {@code AuditService.record(...)} call it already makes — see
     * {@code ManifestServiceImpl#dispatch}, {@code RolePermissionServiceImpl#assign} for the
     * pattern.
     */
    public static void recordChange(String module, String submodule, String description,
                                    String entityType, String entityId,
                                    Map<String, Object> oldValue, Map<String, Object> newValue) {
        CURRENT.set(new Entry(module, submodule, description, entityType, entityId, oldValue, newValue));
    }

    public static Entry get() {
        return CURRENT.get();
    }

    /** Must run in a {@code finally} in the filter — servlet threads are reused. */
    public static void clear() {
        CURRENT.remove();
    }
}
