package com.courier.modules.followup.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code OPEN -> IN_PROGRESS -> COMPLETED}, with {@code RESCHEDULED} reachable from
 * either non-terminal state and looping back into {@code IN_PROGRESS}, and
 * {@code CANCELLED} reachable from any non-terminal state. {@link #canTransitionTo} is
 * the single source of truth {@code FollowUpServiceImpl}'s transition guard enforces —
 * a status is never set on the entity without going through it. {@code COMPLETED} and
 * {@code CANCELLED} are terminal: a completed/cancelled follow-up is never edited
 * again except through its own history trail.
 */
public enum FollowUpStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    RESCHEDULED,
    CANCELLED;

    private static final Map<FollowUpStatus, Set<FollowUpStatus>> ALLOWED = new EnumMap<>(FollowUpStatus.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(IN_PROGRESS, RESCHEDULED, COMPLETED, CANCELLED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(RESCHEDULED, COMPLETED, CANCELLED));
        ALLOWED.put(RESCHEDULED, EnumSet.of(IN_PROGRESS, COMPLETED, CANCELLED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(FollowUpStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(FollowUpStatus.class));
    }

    public boolean canTransitionTo(FollowUpStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
