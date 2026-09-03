package com.courier.modules.districtfreight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Result of a District Level Freight Excel import — a dry-run preview (nothing written)
 * or the real, committed import. Blank rows and the ODA note row are silently ignored,
 * not counted here: a row only appears once it carries a From Station, a District and all
 * six slab rates.
 */
@Schema(name = "ImportSummaryResponse", description = "Row-level summary of a District Level Freight Excel import")
public record ImportSummaryResponse(
        boolean dryRun,
        int totalDataRows,
        int succeeded,
        int failed,
        List<ImportRowResult> rows
) {
}
