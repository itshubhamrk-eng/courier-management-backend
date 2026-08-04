package com.courier.modules.customer.domain;

/**
 * Whether a customer is a private individual or a registered business. GST is
 * mandatory only for {@link #BUSINESS} — see {@link Customer#applyInvariants()}.
 */
public enum CustomerType {
    INDIVIDUAL,
    BUSINESS
}
