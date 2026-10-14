package com.courier.modules.communication.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "SubmitTemplateToMetaRequest", description = "Both fields optional")
public record SubmitTemplateToMetaRequest(
        @Size(max = 20) @Schema(description = "UTILITY (default), MARKETING or AUTHENTICATION") String category,
        @Size(max = 10) @Schema(description = "Meta language code, default en_US") String language
) {
}
