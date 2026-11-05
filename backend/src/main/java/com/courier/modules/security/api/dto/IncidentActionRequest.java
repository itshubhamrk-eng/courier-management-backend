package com.courier.modules.security.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Body of ban / dismiss / unban. Carries only the free-text note: who is acting always
 * comes from the authenticated principal, never from the request.
 */
@Schema(description = "Decision note for a security incident action")
public record IncidentActionRequest(
        @Schema(description = "Optional reason, trimmed; max 1000 characters", maxLength = 1000)
        @Size(max = 1000, message = "must be at most 1000 characters")
        String note
) {

    /** Trims; a blank note is stored as no note. */
    public IncidentActionRequest {
        if (note != null) {
            note = note.trim();
            if (note.isEmpty()) {
                note = null;
            }
        }
    }
}
