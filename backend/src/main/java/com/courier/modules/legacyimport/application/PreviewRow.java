package com.courier.modules.legacyimport.application;

import java.util.List;

/** One classified file row. {@code differences} is only filled for EXISTING rows. */
public record PreviewRow(int rowNo, String consignmentNo, RowOutcome outcome, String reason,
                         List<String> differences, String origin) {
}
