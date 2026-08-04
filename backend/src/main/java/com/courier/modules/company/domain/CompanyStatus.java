package com.courier.modules.company.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a company.
 *
 * <p>The legal transitions live here rather than in the service, so no call path can
 * invent one. {@code EXPIRED} and {@code SUSPENDED} are both recoverable — a customer
 * who pays is reactivated, not recreated — while a deleted company is soft-deleted and
 * disappears from every read instead of becoming a terminal status.
 *
 * <pre>
 *   TRIAL ──activate──> ACTIVE ──suspend──> SUSPENDED ──activate──> ACTIVE
 *     │                   │                     │
 *     └──expire───────────┴─────────────────────┘
 *                         │
 *                      EXPIRED ──activate──> ACTIVE
 *   INACTIVE ──activate──> ACTIVE
 *
 *   anything but INACTIVE ──deactivate──> INACTIVE ──activate──> ACTIVE
 * </pre>
 *
 * <p><b>{@code INACTIVE} and {@code SUSPENDED} are not the same thing</b>, which is why
 * deactivate is its own operation rather than a synonym. Suspension is punitive and
 * carries a reason that support will quote back; deactivation is a switch an operator
 * flips on a company that is merely dormant. Both stop authentication, but only one of
 * them is an accusation.
 */
public enum CompanyStatus {

    /** Inside the plan's trial window. Fully usable; the clock is running. */
    TRIAL,

    /** Paying and usable. */
    ACTIVE,

    /** Switched off administratively, without the punitive meaning of SUSPENDED. */
    INACTIVE,

    /** Blocked — non-payment, abuse, or a support hold. Users cannot authenticate. */
    SUSPENDED,

    /** Trial or subscription window ended. Users cannot authenticate. */
    EXPIRED;

    private static final Set<CompanyStatus> CAN_ACTIVATE =
            EnumSet.of(TRIAL, INACTIVE, SUSPENDED, EXPIRED);
    private static final Set<CompanyStatus> CAN_SUSPEND = EnumSet.of(TRIAL, ACTIVE);
    private static final Set<CompanyStatus> CAN_EXPIRE = EnumSet.of(TRIAL, ACTIVE);
    private static final Set<CompanyStatus> CAN_DEACTIVATE =
            EnumSet.of(TRIAL, ACTIVE, SUSPENDED, EXPIRED);

    /** Statuses whose users may authenticate and whose data may be served. */
    private static final Set<CompanyStatus> OPERATIONAL = EnumSet.of(TRIAL, ACTIVE);

    public boolean isOperational() {
        return OPERATIONAL.contains(this);
    }

    public boolean canTransitionTo(CompanyStatus target) {
        return switch (target) {
            case ACTIVE -> CAN_ACTIVATE.contains(this);
            case SUSPENDED -> CAN_SUSPEND.contains(this);
            case EXPIRED -> CAN_EXPIRE.contains(this);
            // Deactivation is legal from every state except itself: an operator
            // switching off a dormant company should not first have to work out
            // whether its trial lapsed or its card bounced.
            case INACTIVE -> CAN_DEACTIVATE.contains(this);
            case TRIAL -> false;
        };
    }
}
