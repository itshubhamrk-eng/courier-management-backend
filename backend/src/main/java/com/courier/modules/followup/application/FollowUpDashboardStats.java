package com.courier.modules.followup.application;

/** Backs the Operations Dashboard's Follow-up widget — four live buckets, click-through
 *  to the filtered list. {@code urgent} counts every open (non-terminal) URGENT-priority
 *  follow-up regardless of due date, matching the widget's own "🔴 Urgent" tile. */
public record FollowUpDashboardStats(
        long overdue,
        long dueToday,
        long upcoming,
        long urgent
) {
}
