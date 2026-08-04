package com.courier.modules.finance.api.dto;

import com.courier.modules.finance.domain.TopupRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Query parameters of {@code GET /api/v1/branch-wallet/topup-requests}. */
@Schema(name = "TopupRequestSearchRequest", description = "Top-up request search filters")
public record TopupRequestSearchRequest(
        @Schema(description = "Company admin only — a branch caller's own branch is always used")
        UUID branchId,
        TopupRequestStatus status
) {
    public static TopupRequestSearchRequest empty() {
        return new TopupRequestSearchRequest(null, null);
    }
}
