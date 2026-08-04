package com.courier.modules.finance.application.command;

import com.courier.modules.finance.domain.ReferenceType;
import com.courier.modules.finance.domain.SubTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A manual debit from a branch wallet, by a company admin.
 *
 * <p>Deliberately the mirror image of {@link CreditCommand} rather than one command with a
 * direction field: credit and debit are different authority and different consequences, and
 * a single "amount, signed" API is how a rounding bug turns a refund into a charge.
 *
 * @param branchId           whose wallet
 * @param amount             strictly positive; the direction is the operation, not the sign
 * @param subTransactionType why; must be a reason that can appear on a debit
 * @param referenceType      what {@code referenceId} points at; defaults to MANUAL
 * @param referenceId        the document this debit answers to, if any
 * @param remarks            free text, shown on the statement
 */
public record DebitCommand(
        UUID branchId,
        BigDecimal amount,
        SubTransactionType subTransactionType,
        ReferenceType referenceType,
        String referenceId,
        String remarks
) {
}
