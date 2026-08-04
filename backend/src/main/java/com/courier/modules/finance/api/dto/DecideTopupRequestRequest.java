package com.courier.modules.finance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Body of the approve/reject endpoints. Both optional — a decision needs no reason. */
@Schema(name = "DecideTopupRequestRequest", description = "Approve or reject a top-up request")
public record DecideTopupRequestRequest(
        @Size(max = 500)
        @Schema(description = "Shown back to the branch", example = "Approved against this month's float")
        String remarks
) {
}
