package com.courier.modules.support.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The marketing site's Contact/Request-a-Quote form submission — mirrors that form's fields
 * exactly (name, company, email, phone, serviceRequired, message). Lands as a {@code Ticket}
 * via {@code PublicLeadService}, not a new table: it needs no workflow of its own beyond
 * what tickets already have (status, priority, an admin picking it up).
 */
@Schema(name = "PublicLeadRequest")
public record PublicLeadRequest(
        @NotBlank(message = "Name is required") @Size(max = 150) String name,
        @Size(max = 150) String company,
        @NotBlank(message = "Email is required") @Email @Size(max = 255) String email,
        @NotBlank(message = "Phone is required") @Size(max = 20) String phone,
        @NotBlank(message = "Service required") @Size(max = 100) String serviceRequired,
        @NotBlank(message = "Message is required") @Size(max = 2000) String message
) {
}
