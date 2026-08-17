package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangeStatusRequest")
public record ChangeStatusRequest(
        @NotNull TicketStatus status,
        @Size(max = 1000) String remarks
) {
}
