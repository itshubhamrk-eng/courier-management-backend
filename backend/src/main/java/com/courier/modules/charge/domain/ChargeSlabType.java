package com.courier.modules.charge.domain;

/**
 * Which dimension a {@code SLAB}-type {@link ChargeSetting} bands on. Meaningless for
 * {@link ChargeType#FACTOR} settings, which carry no slab type at all.
 */
public enum ChargeSlabType {
    /** Bands on weight only — {@code fromKg}/{@code toKg} required, {@code fromKm}/{@code toKm} left null. */
    KG,
    /** Bands on distance only — {@code fromKm}/{@code toKm} required, {@code fromKg}/{@code toKg} left null. */
    KM,
    /** Bands on both — all four of {@code fromKg}/{@code toKg}/{@code fromKm}/{@code toKm} required. */
    BOTH
}
