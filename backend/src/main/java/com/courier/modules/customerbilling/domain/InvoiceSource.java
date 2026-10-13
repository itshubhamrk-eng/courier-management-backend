package com.courier.modules.customerbilling.domain;

/** Who generated an invoice: the billing-cycle job, or an admin. */
public enum InvoiceSource {
    AUTO,
    MANUAL
}
