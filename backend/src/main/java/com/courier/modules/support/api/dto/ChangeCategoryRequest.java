package com.courier.modules.support.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "ChangeCategoryRequest")
public record ChangeCategoryRequest(
        @NotNull UUID categoryId,
        UUID subCategoryId,
        @Size(max = 1000) String remarks
) {
}
