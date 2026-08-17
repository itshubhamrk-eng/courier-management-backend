package com.courier.modules.support.api.dto;

import com.courier.modules.support.domain.TicketPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "CreateTicketRequest", description = "Raise a support ticket")
public record CreateTicketRequest(

        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 8000) String description,

        @NotNull UUID categoryId,
        UUID subCategoryId,

        @Schema(description = "Defaults to MEDIUM when omitted") TicketPriority priority,

        @Schema(description = "Pre-filled when raised from a shipment's own page") UUID relatedShipmentId,
        UUID relatedCustomerId,
        UUID relatedBranchId,

        @Schema(description = "SUPER_ADMIN only — the tenant this ticket is raised for") UUID companyId
) {
}
