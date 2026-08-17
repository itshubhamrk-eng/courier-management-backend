package com.courier.modules.support.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTicketSubCategoryRequest(@NotNull UUID categoryId, @NotBlank @Size(max = 100) String name) {
}
