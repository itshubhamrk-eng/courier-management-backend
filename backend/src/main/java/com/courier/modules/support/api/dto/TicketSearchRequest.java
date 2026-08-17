package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.TicketPriority;
import com.courier.modules.support.domain.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "TicketSearchRequest", description = "Ticket search filters")
public record TicketSearchRequest(
        TicketStatus status,
        TicketPriority priority,
        UUID categoryId,
        UUID subCategoryId,
        UUID relatedBranchId,
        UUID assigneeUserId,
        Instant createdFrom,
        Instant createdTo,
        String search,
        @Schema(description = "SUPER_ADMIN only — narrows the cross-tenant view to one company")
        UUID companyId
) {
    public static TicketSearchRequest empty() {
        return new TicketSearchRequest(null, null, null, null, null, null, null, null, null, null);
    }
}
