package com.courier.modules.company.domain;

/**
 * What part of the business a role belongs to.
 *
 * <p>Deliberately <b>not</b> "system versus custom" — {@code isSystemRole} already
 * records that, and a second field meaning the same thing is a field that will
 * eventually contradict the first. This is the functional grouping the roles screen
 * renders under, and the axis reports slice by ("how many finance users across our
 * branches?").
 *
 * <p>A company's own roles are typed too: a custom "Night Shift Supervisor" is
 * {@link #OPERATIONS}, not a category of its own.
 */
public enum RoleType {

    /** Runs the company itself: users, roles, settings, branches. */
    ADMINISTRATION,

    /** Moves parcels: booking, sorting, dispatch, delivery. */
    OPERATIONS,

    /** Billing, invoicing, COD reconciliation, rate cards. */
    FINANCE,

    /** Customer-facing: tracking queries, complaints, escalations. */
    SUPPORT,

    /** Sees the business without changing it: auditors, analysts. */
    READ_ONLY
}
