package com.courier.modules.finance.domain;

/**
 * Lifecycle of a {@link WalletTopupRequest}. Terminal once decided — there is no path back
 * to {@code PENDING} from {@code APPROVED}/{@code REJECTED}; a mistaken decision is
 * corrected with a new request, not by reopening the old one, the same "never edit, only
 * add" rule the ledger itself follows.
 */
public enum TopupRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
