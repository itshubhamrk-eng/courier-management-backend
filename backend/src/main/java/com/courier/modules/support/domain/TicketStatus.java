package com.courier.modules.support.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code OPEN -> ASSIGNED -> IN_PROGRESS -> (WAITING_FOR_USER | WAITING_FOR_INTERNAL_TEAM)
 * -> RESOLVED -> CLOSED}, with {@code REOPENED} looping back from either terminal state.
 * {@link #canTransitionTo} is the single source of truth for what
 * {@code TicketServiceImpl}'s transition guard allows — a status is never set on the
 * entity without going through it.
 */
public enum TicketStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    WAITING_FOR_USER,
    WAITING_FOR_INTERNAL_TEAM,
    RESOLVED,
    CLOSED,
    REOPENED;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(ASSIGNED, IN_PROGRESS));
        ALLOWED.put(ASSIGNED, EnumSet.of(IN_PROGRESS, WAITING_FOR_USER, WAITING_FOR_INTERNAL_TEAM, RESOLVED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(WAITING_FOR_USER, WAITING_FOR_INTERNAL_TEAM, RESOLVED));
        ALLOWED.put(WAITING_FOR_USER, EnumSet.of(IN_PROGRESS, RESOLVED));
        ALLOWED.put(WAITING_FOR_INTERNAL_TEAM, EnumSet.of(IN_PROGRESS, RESOLVED));
        ALLOWED.put(RESOLVED, EnumSet.of(CLOSED, REOPENED));
        ALLOWED.put(CLOSED, EnumSet.of(REOPENED));
        ALLOWED.put(REOPENED, EnumSet.of(ASSIGNED, IN_PROGRESS));
    }

    public boolean canTransitionTo(TicketStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }
}
