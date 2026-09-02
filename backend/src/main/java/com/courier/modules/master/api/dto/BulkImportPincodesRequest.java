package com.courier.modules.master.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code POST /api/v1/global-masters/pincodes/bulk-import}.
 *
 * <p>Each range is probed inclusively: every code from {@code fromCode} to {@code toCode},
 * same digit width, is looked up in the postal directory and kept only if it resolves to a
 * real post office. Several disjoint ranges in one call is how a state's real (non-contiguous)
 * pincode blocks are covered without wastefully scanning the gaps between its cities.
 */
@Schema(name = "BulkImportPincodesRequest", description = "Numeric pincode ranges to probe and import")
public record BulkImportPincodesRequest(
        @NotEmpty @Size(max = 50) @Valid List<Range> ranges
) {
    public record Range(
            @Pattern(regexp = "^[0-9]{4,10}$", message = "must be 4 to 10 digits")
            @Schema(example = "411001") String fromCode,
            @Pattern(regexp = "^[0-9]{4,10}$", message = "must be 4 to 10 digits")
            @Schema(example = "411062") String toCode
    ) {
    }
}
