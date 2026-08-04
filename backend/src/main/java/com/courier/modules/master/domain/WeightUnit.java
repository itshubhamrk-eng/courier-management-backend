package com.courier.modules.master.domain;

/**
 * Unit a weight slab is expressed in.
 *
 * <p>Deliberately this module's own enum rather than an import of
 * {@code company.domain.WeightUnit}: the cross-feature rule in
 * {@code MEMORY/ARCHITECTURE.md} §1 forbids reaching into another feature's domain, and
 * the company copy means "the unit this company displays weights in", which is a
 * different fact from "the unit this slab is measured in".
 */
public enum WeightUnit {

    KG(1_000),
    GRAM(1),
    POUND(454);

    private final int gramsPerUnit;

    WeightUnit(int gramsPerUnit) {
        this.gramsPerUnit = gramsPerUnit;
    }

    /**
     * Approximate grams in one unit. Used only to order the units in a picker and to
     * explain an overlap message — never to convert a chargeable weight, which is a rate
     * card's job and must not be done with an int.
     */
    public int gramsPerUnit() {
        return gramsPerUnit;
    }
}
