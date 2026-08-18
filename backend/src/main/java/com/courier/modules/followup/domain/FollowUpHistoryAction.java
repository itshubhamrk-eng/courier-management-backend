package com.courier.modules.followup.domain;

/** One row per entry in a follow-up's own timeline — every status change, every
 *  reschedule, every assignment and every note is recorded here, never edited. */
public enum FollowUpHistoryAction {
    CREATED,
    UPDATED,
    STATUS_CHANGED,
    RESCHEDULED,
    ASSIGNED,
    NOTE_ADDED
}
