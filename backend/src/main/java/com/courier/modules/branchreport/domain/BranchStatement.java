package com.courier.modules.branchreport.domain;

import com.courier.modules.finance.application.WalletService;
import com.courier.modules.finance.domain.WalletTransaction;
import com.courier.modules.shipment.domain.VendorAuditRow;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One branch's statement for a period: order/charge figures from the vendor audit, wallet
 * figures from the ledger, and every ledger transaction in the period (ascending).
 *
 * @param from inclusive (IST date)
 * @param to   inclusive (IST date)
 */
public record BranchStatement(
        UUID branchId,
        String branchCode,
        String branchName,
        LocalDate from,
        LocalDate to,
        VendorAuditRow audit,
        WalletService.PeriodSummary wallet,
        List<WalletTransaction> transactions
) {
}
