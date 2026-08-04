package com.courier.modules.company.api.dto;

import com.courier.modules.company.domain.BranchStatus;
import com.courier.modules.company.domain.BranchType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Query parameters of {@code GET /api/v1/branches}, bound as a parameter object.
 * {@code companyId} is meaningful only for a SUPER_ADMIN; for a company user the result is
 * pinned to their company (admins) or their own branches (everyone else).
 */
@Schema(name = "BranchSearchRequest", description = "Branch search filters")
public record BranchSearchRequest(
        UUID companyId,
        Set<BranchType> branchType,
        Set<BranchStatus> status,
        UUID managerId,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 20) String postalCode,
        Boolean allowBooking,
        Boolean allowDelivery,
        Boolean allowPickup,
        @Size(max = 100)
        @Schema(description = "Free text over code, name, email, city and postal code")
        String search
) {
        public static BranchSearchRequest empty() {
                return new BranchSearchRequest(null, null, null, null, null, null, null,
                        null, null, null, null);
        }
}
