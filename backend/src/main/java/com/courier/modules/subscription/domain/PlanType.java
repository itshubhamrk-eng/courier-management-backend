package com.courier.modules.subscription.domain;

/**
 * Commercial tier of a subscription plan.
 *
 * <p>Two tiers carry behaviour rather than being labels, and both are enforced in
 * {@link SubscriptionPlan}:
 * <ul>
 *   <li>{@link #TRIAL} — must be free. A trial with a monthly price is a billing
 *       incident waiting to happen.</li>
 *   <li>{@link #ENTERPRISE} — every quota is unlimited. Contracts at this tier are
 *       negotiated, not metered.</li>
 * </ul>
 *
 * <p>Ordinals are never persisted ({@code @Enumerated(STRING)}), so constants may be
 * reordered, but a name is part of the API contract and must not be renamed.
 */
public enum PlanType {

    TRIAL,
    BASIC,
    STANDARD,
    PREMIUM,
    ENTERPRISE;

    /** Trials are free by definition; the price fields must be zero. */
    public boolean requiresZeroPrice() {
        return this == TRIAL;
    }

    /** Enterprise ignores every numeric quota — see {@link SubscriptionPlan#UNLIMITED}. */
    public boolean hasUnlimitedQuotas() {
        return this == ENTERPRISE;
    }
}
