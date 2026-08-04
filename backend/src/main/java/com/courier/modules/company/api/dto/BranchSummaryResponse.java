package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.BranchStatus;
import com.courier.modules.company.domain.BranchType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Compact projection for list responses. */
@Schema(name = "BranchSummaryResponse", description = "Branch, list projection")
public record BranchSummaryResponse(
        UUID id, UUID companyId, String branchCode, String branchName,
        BranchType branchType, BranchStatus status,
        String city, String state, String postalCode, UUID managerId,
        boolean allowBooking, boolean allowDelivery,
        Instant createdDate, Long version
) {
}
