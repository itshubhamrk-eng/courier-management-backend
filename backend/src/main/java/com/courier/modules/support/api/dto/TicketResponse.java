package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.TicketPriority;
import com.courier.modules.support.domain.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Flat, ids only — same convention {@code TopupRequestResponse} uses; the frontend
 *  carries its own directories for labels. */
@Schema(name = "TicketResponse", description = "A support ticket")
public record TicketResponse(
        UUID id,
        UUID companyId,
        String ticketNumber,
        String subject,
        String description,
        UUID categoryId,
        UUID subCategoryId,
        TicketPriority priority,
        TicketStatus status,
        UUID relatedShipmentId,
        UUID relatedCustomerId,
        UUID relatedBranchId,
        UUID createdByUserId,
        UUID assigneeUserId,
        boolean escalated,
        Instant firstResponseAt,
        Instant slaFirstResponseDueAt,
        Instant slaResolutionDueAt,
        @Schema(description = "ON_TRACK | WARNING | BREACHED | NO_SLA — NO_SLA when the company has no "
                + "active SLA rule for this ticket's priority") String slaStatus,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
