package com.courier.modules.finance.domain;

import java.util.UUID;

/**
 * @param companyId always set by the service, never taken from the request
 * @param branchId  optional — a company admin narrows to one branch; a branch caller has
 *                  this pinned to their own by the service
 * @param status    optional — PENDING/APPROVED/REJECTED
 */
public record WalletTopupRequestCriteria(UUID companyId, UUID branchId, TopupRequestStatus status) {

    public static WalletTopupRequestCriteria none() {
        return new WalletTopupRequestCriteria(null, null, null);
    }

    public WalletTopupRequestCriteria scopedTo(UUID enforcedCompanyId) {
        return new WalletTopupRequestCriteria(enforcedCompanyId, branchId, status);
    }
}
