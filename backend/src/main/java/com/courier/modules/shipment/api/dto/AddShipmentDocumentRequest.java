package com.courier.modules.shipment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/shipments/{id}/documents}. {@code documentType} is one of
 * {@code INVOICE}, {@code EWAY_BILL}, {@code PACKING_LIST}, {@code LR_COPY}, {@code POD}.
 *
 * <p>There is no file-storage backend in this project yet — {@code documentUrl} is
 * supplied by the caller, the same honesty note {@code features/company}'s
 * {@code CompanyLogo} carries.
 */
@Schema(name = "AddShipmentDocumentRequest", description = "A reference to an already-hosted document")
public record AddShipmentDocumentRequest(
        @NotBlank String documentType,
        @NotBlank @Size(max = 255) String documentName,
        @NotBlank @Size(max = 1000) String documentUrl,
        @Size(max = 500) String remarks
) {
}
