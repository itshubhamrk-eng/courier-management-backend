package com.courier.modules.legacyimport.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One data row of the old software's consignment export. {@code error} is non-null when the
 * row cannot be imported (the other fields are then best-effort and may be null); it never
 * stops the rest of the file from being read.
 */
public record LegacyRow(int rowNo, String consignmentNo, LocalDate date, String consignor,
                        String consignee, String from, String to, int packages,
                        BigDecimal weight, BigDecimal total, String error) {
}
