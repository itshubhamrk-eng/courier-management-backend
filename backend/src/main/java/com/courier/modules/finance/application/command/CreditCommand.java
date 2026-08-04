package com.courier.modules.finance.application.command;

import com.courier.modules.finance.domain.ReferenceType;
import com.courier.modules.finance.domain.SubTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A manual credit to a branch wallet, by a company admin.
 *
 * @param branchId           whose wallet — required for an admin, who has no branch of
 *                           their own to fall back on
 * @param amount             strictly positive, in the wallet's currency
 * @param subTransactionType why; must be a reason that can appear on a credit
 * @param referenceType      what {@code referenceId} points at; defaults to MANUAL
 * @param referenceId        the document this credit answers to, if any
 * @param remarks            free text, shown on the statement
 */
public record CreditCommand(
        UUID branchId,
        BigDecimal amount,
        SubTransactionType subTransactionType,
        ReferenceType referenceType,
        String referenceId,
        String remarks
) {
}
