package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.TicketPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpsertSlaRuleRequest", description = "Create-or-replace the SLA target for one priority tier")
public record UpsertSlaRuleRequest(
        @NotNull TicketPriority priority,
        @NotNull @Min(1) Integer firstResponseMinutes,
        @NotNull @Min(1) Integer resolutionMinutes
) {
}
