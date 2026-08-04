package com.courier.modules.customer.api.dto;

import com.courier.modules.customer.domain.CustomerStatus;
import com.courier.modules.customer.domain.CustomerType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Compact projection for list responses. */
@Schema(name = "CustomerSummaryResponse", description = "Customer, list projection")
public record CustomerSummaryResponse(
        UUID id, String customerCode, CustomerType customerType,
        String displayName, String mobile, String email,
        CustomerStatus status, Instant createdDate, Long version
) {
}
