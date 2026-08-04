package com.courier.modules.rate.domain;

/**
 * Unit a rate's weight slab is expressed in.
 *
 * <p>Deliberately this module's own enum rather than an import of
 * {@code master.domain.WeightUnit}: the cross-feature rule in
 * {@code MEMORY/ARCHITECTURE.md} §1 forbids reaching into another feature's domain, and
 * "the unit this rate card is priced in" is a different fact from "the unit a master
 * weight slab is measured in", even though the two enums happen to list the same
 * constants today — the same reasoning {@code master.domain.WeightUnit} and
 * {@code master.domain.DistanceUnit} already document.
 */
public enum WeightUnit {
    KG,
    GRAM,
    POUND
}
