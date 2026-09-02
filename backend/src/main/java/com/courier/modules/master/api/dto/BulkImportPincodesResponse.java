package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Tally of a bulk-import run, summed across every range in the request. */
@Schema(name = "BulkImportPincodesResponse", description = "Bulk pincode import tally")
public record BulkImportPincodesResponse(
        @Schema(description = "Total candidate codes probed against the postal directory") int probed,
        @Schema(description = "New pincodes created") int created,
        @Schema(description = "Already existed in this company — skipped, not re-created") int alreadyExisted,
        @Schema(description = "Postal directory has no record of this code") int noPostalMatch,
        @Schema(description = "Resolved but failed to save — see server logs") int failed
) {
}
