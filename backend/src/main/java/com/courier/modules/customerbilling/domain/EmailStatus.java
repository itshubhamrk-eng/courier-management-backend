package com.courier.modules.customerbilling.domain;

/** Whether the approved invoice reached the customer's mailbox (as far as the provider reports). */
public enum EmailStatus {
    NOT_SENT,
    SENT,
    FAILED,
    /** The customer has no e-mail address on file. */
    NO_EMAIL
}
