package com.courier.modules.company.application.command;

import com.courier.modules.company.domain.BranchType;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Input to {@code BranchService.update}. Full replacement of the editable fields.
 *
 * <p>Absent by design: {@code companyId}, {@code status} (activate/deactivate endpoints)
 * and {@code managerId} (assign-manager endpoint).
 *
 * @param expectedVersion the version last read; a stale value is rejected with 409
 */
public record UpdateBranchCommand(
        String branchCode, String branchName, BranchType branchType,
        String email, String mobile, String alternateMobile,
        String addressLine1, String addressLine2, String country, String state, String city,
        String district, String taluka, String postalCode,
        BigDecimal latitude, BigDecimal longitude,
        LocalTime openingTime, LocalTime closingTime, String workingDays,
        Boolean allowBooking, Boolean allowDelivery, Boolean allowPickup,
        Boolean allowManifest, Boolean allowCashCollection, Boolean allowWallet,
        Boolean instantCommission,
        String remarks,
        String gstNumber, String panNumber,
        BigDecimal gstPercentage, BigDecimal commissionOnOtherCharges,
        BigDecimal commissionOnBasicFreight, BigDecimal companyServiceChargePercentage,
        BigDecimal drsChargePerQty,
        Long expectedVersion
) {
}
