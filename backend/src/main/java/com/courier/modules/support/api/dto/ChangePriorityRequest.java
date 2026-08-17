package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.TicketPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePriorityRequest")
public record ChangePriorityRequest(
        @NotNull TicketPriority priority,
        @Size(max = 1000) String remarks
) {
}
