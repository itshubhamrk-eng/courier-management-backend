package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.BranchStatus;
import com.courier.modules.company.domain.BranchType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Compact projection for list responses. {@code gstPercentage} rides along the same way
 *  {@code postalCode} already does — Shipment Booking's own live pricing preview reads it
 *  off the caller's own branch (from {@code /branches/directory}) to keep its Other Charges
 *  GST math in step with what {@code ShipmentServiceImpl.copyCharge} persists at booking. */
@Schema(name = "BranchSummaryResponse", description = "Branch, list projection")
public record BranchSummaryResponse(
        UUID id, UUID companyId, String branchCode, String branchName,
        BranchType branchType, BranchStatus status,
        String city, String state, String postalCode, UUID managerId,
        boolean allowBooking, boolean allowDelivery,
        Instant createdDate, Long version,
        BigDecimal gstPercentage
) {
}
