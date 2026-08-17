package com.courier.modules.support.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "TicketDetailResponse", description = "A ticket with its full conversation and timeline")
public record TicketDetailResponse(
        TicketResponse ticket,
        List<TicketMessageResponse> messages,
        List<TicketAttachmentResponse> attachments,
        List<TicketStatusHistoryResponse> statusHistory,
        List<TicketAssignmentHistoryResponse> assignmentHistory
) {
}
