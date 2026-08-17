package com.courier.modules.support.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketCategoryRequest(@NotBlank @Size(max = 100) String name) {
}
