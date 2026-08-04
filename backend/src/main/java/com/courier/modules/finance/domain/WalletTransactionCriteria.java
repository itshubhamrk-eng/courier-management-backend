package com.courier.modules.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Filter criteria for a ledger search. Every field optional; null means "do not constrain".
 * Lives in {@code domain} so controller and service share one shape.
 *
 * <p>{@link #walletId()} is not a filter the caller supplies — the service pins it to the
 * wallet the caller resolved and is allowed to read. A criteria object that reached the
 * repository without it would list another branch's statement.
 *
 * @param walletId      the wallet whose ledger this is; always set by the service
 * @param companyId      always set by the service, never taken from the request
 * @param transactionTypes    CR and/or DR
 * @param subTransactionTypes match any of these reasons
 * @param referenceTypes      match any of these reference kinds
 * @param paymentStatuses     match any of these payment states
 * @param referenceId   exact match on the referenced document
 * @param transactionNo exact match on the entry number
 * @param paymentReference exact match on the gateway payment id
 * @param from          inclusive lower bound on {@code createdAt}
 * @param to            exclusive upper bound on {@code createdAt}
 * @param minAmount     inclusive lower bound on the amount
 * @param maxAmount     inclusive upper bound on the amount
 * @param search        free text over entry number, remarks, reference id and payment id
 */
public record WalletTransactionCriteria(
        UUID walletId,
        UUID companyId,
        Set<TransactionType> transactionTypes,
        Set<SubTransactionType> subTransactionTypes,
        Set<ReferenceType> referenceTypes,
        Set<PaymentStatus> paymentStatuses,
        String referenceId,
        String transactionNo,
        String paymentReference,
        Instant from,
        Instant to,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String search
) {

    public static WalletTransactionCriteria none() {
        return new WalletTransactionCriteria(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    /** Pins the search to one wallet of one company. The service always calls this. */
    public WalletTransactionCriteria scopedTo(UUID enforcedWalletId, UUID enforcedCompanyId) {
        return new WalletTransactionCriteria(enforcedWalletId, enforcedCompanyId,
                transactionTypes, subTransactionTypes, referenceTypes, paymentStatuses,
                referenceId, transactionNo, paymentReference, from, to, minAmount, maxAmount, search);
    }
}
