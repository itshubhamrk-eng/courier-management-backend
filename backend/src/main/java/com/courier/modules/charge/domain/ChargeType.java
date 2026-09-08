package com.courier.modules.charge.domain;

/**
 * How a {@link ChargeSetting} computes its value.
 *
 * <p>{@code FACTOR} — a single flat/percentage value, independent of distance or weight.
 * {@code SLAB} — the value depends on a KG and/or KM band; see {@link ChargeSlabType}.
 */
public enum ChargeType {
    FACTOR,
    SLAB
}
