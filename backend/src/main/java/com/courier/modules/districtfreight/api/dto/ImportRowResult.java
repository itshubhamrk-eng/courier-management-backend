package com.courier.modules.districtfreight.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One data row's outcome. {@code rowNumber} is the spreadsheet's own 1-based row number
 * (header is row 1), so it points a user straight back at the offending line in Excel.
 */
@Schema(name = "ImportRowResult", description = "Outcome of one row of a District Level Freight Excel import")
public record ImportRowResult(
        int rowNumber,
        String fromStation,
        String district,
        @Schema(description = "WOULD_CREATE / WOULD_UPDATE (preview) or CREATED / UPDATED (commit), or ERROR")
        String outcome,
        @Schema(description = "Null on success") String message
) {
}
