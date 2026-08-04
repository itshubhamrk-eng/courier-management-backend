package com.courier.modules.finance.domain;

/**
 * Direction of a wallet ledger entry.
 *
 * <p>Two values only, and the stored form is the two-letter accounting code rather than
 * the English word: statements, exports and downstream settlement files all speak
 * CR/DR, and a wallet supports nothing else.
 */
public enum TransactionType {

    /** Credit — the branch's available balance goes up. */
    CR("Credit"),

    /** Debit — the branch's available balance goes down. */
    DR("Debit");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCredit() {
        return this == CR;
    }

    public boolean isDebit() {
        return this == DR;
    }
}
