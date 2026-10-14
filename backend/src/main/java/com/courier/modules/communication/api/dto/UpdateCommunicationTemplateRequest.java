package com.courier.modules.communication.api.dto;

import com.courier.modules.communication.domain.TemplateStatus;
import com.courier.modules.communication.domain.TemplateButton;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

@Schema(name = "UpdateCommunicationTemplateRequest")
public record UpdateCommunicationTemplateRequest(
        @NotBlank @Size(max = 150) String templateName,
        @Size(max = 255) String subject,
        @NotBlank String content,
        @NotNull TemplateStatus status,
        @NotNull @PositiveOrZero
        @Schema(description = "Version last read; a stale value returns 409") Long version,
        @Schema(description = "UTILITY / MARKETING / AUTHENTICATION; omitted = unchanged") String category,
        @Schema(description = "en_US, en, hi or mr; omitted = unchanged") String language,
        @Size(max = 60) @Schema(description = "WhatsApp header text; omitted = unchanged, blank = remove") String headerText,
        @Size(max = 60) @Schema(description = "WhatsApp footer; omitted = unchanged, blank = remove") String footerText,
        @Schema(description = "Link / call buttons; omitted = unchanged, [] = remove all") List<TemplateButton> buttons,
        @Schema(description = "Sample per {{variable}}; omitted = unchanged, {} = built-in samples") Map<String, String> variableSamples
) {
    /** The original five-field shape — nothing new is touched. */
    public UpdateCommunicationTemplateRequest(String templateName, String subject, String content,
                                              TemplateStatus status, Long version) {
        this(templateName, subject, content, status, version, null, null, null, null, null, null);
    }
}
