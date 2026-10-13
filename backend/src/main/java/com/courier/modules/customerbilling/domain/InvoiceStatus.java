package com.courier.modules.customerbilling.domain;

/** Where a business customer invoice stands. CANCELLED invoices free their shipments to be billed again. */
public enum InvoiceStatus {
    UNPAID,
    PARTLY_PAID,
    PAID,
    CANCELLED
}
