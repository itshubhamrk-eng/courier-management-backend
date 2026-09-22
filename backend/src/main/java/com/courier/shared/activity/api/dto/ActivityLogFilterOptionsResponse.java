package com.courier.shared.activity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Backs the Module/Action/Entity Type dropdowns on the Activity Log screen — the distinct
 *  values actually logged for the caller's company, not a hardcoded list. */
@Schema(name = "ActivityLogFilterOptionsResponse")
public record ActivityLogFilterOptionsResponse(
        List<String> modules,
        List<String> actions,
        List<String> entityTypes
) {
}
