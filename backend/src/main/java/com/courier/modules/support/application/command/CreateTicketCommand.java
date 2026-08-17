package com.courier.modules.support.application.command;

import com.courier.modules.support.domain.TicketPriority;

import java.util.UUID;

/**
 * @param companyId SUPER_ADMIN only — the tenant this ticket is raised for; ignored (and
 *                   always overwritten by the caller's own company) for every other role
 */
public record CreateTicketCommand(
        String subject,
        String description,
        UUID categoryId,
        UUID subCategoryId,
        TicketPriority priority,
        UUID relatedShipmentId,
        UUID relatedCustomerId,
        UUID relatedBranchId,
        UUID companyId
) {
}
