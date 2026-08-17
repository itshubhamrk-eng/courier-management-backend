package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.TicketPriority;

import java.util.UUID;

public record SlaRuleResponse(
        UUID id, TicketPriority priority, int firstResponseMinutes, int resolutionMinutes,
        boolean active, Long version) {
}
