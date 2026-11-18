package com.courier.modules.legacyimport.application;

import com.courier.shared.exception.BusinessRuleException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyFileParserTest {

    private static final String HEAD = "﻿<table border=\"1\"><thead><tr><th>Date</th><th>Consignment No</th>"
            + "<th>Consignor</th><th>Consignee</th><th>From</th><th>To</th><th>Package</th>"
            + "<th>Actual Weight</th><th>Total</th></tr></thead><tbody>";

    private static String tr(String date, String no, String consignee, String pkg, String weight, String total) {
        return "<tr><td style=\"mso-number-format:'@';\">" + date + "</td><td style=\"mso-number-format:'@';\">" + no
                + "</td><td>RIYANSH MULTITRADE PVT LTD</td><td>" + consignee + "</td><td>BHOSARI</td><td>CHINCHWAD</td><td>"
                + pkg + "</td><td>" + weight + "</td><td>" + total + "</td></tr>";
    }

    private static byte[] html(String... rows) {
        return (HEAD + String.join("", rows) + "</tbody></table>").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesHtmlTableExport() {
        List<LegacyRow> rows = LegacyFileParser.parse("Consignments.xls",
                html(tr("15/11/2026", "SO912C2627215", "VENUBAI VAMAN BHISE", "1", "3.82", "57.30")));

        assertThat(rows).hasSize(1);
        LegacyRow r = rows.get(0);
        assertThat(r.error()).isNull();
        assertThat(r.rowNo()).isEqualTo(1);
        assertThat(r.consignmentNo()).isEqualTo("SO912C2627215");
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(r.consignor()).isEqualTo("RIYANSH MULTITRADE PVT LTD");
        assertThat(r.consignee()).isEqualTo("VENUBAI VAMAN BHISE");
        assertThat(r.from()).isEqualTo("BHOSARI");
        assertThat(r.to()).isEqualTo("CHINCHWAD");
        assertThat(r.packages()).isEqualTo(1);
        assertThat(r.weight()).isEqualByComparingTo("3.82");
        assertThat(r.total()).isEqualByComparingTo("57.30");
    }

    @Test
    void parsesXlsx() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            String[] header = {"Date", "Consignment No", "Consignor", "Consignee", "From", "To", "Package",
                    "Actual Weight", "Total"};
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                h.createCell(i).setCellValue(header[i]);
            }
            Row d = sheet.createRow(1);
            String[] vals = {"03/11/2026", "2611000123", "COSMOS STATIONERY", "DK ENTERPRISES", "PUNE", "SATARA"};
            for (int i = 0; i < vals.length; i++) {
                d.createCell(i).setCellValue(vals[i]);
            }
            d.createCell(6).setCellValue(2);
            d.createCell(7).setCellValue(30.52);
            d.createCell(8).setCellValue(300.0);
            wb.write(out);
            bytes = out.toByteArray();
        }

        List<LegacyRow> rows = LegacyFileParser.parse("x.xlsx", bytes);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).error()).isNull();
        assertThat(rows.get(0).consignmentNo()).isEqualTo("2611000123");
        assertThat(rows.get(0).packages()).isEqualTo(2);
        assertThat(rows.get(0).weight()).isEqualByComparingTo(new BigDecimal("30.52"));
    }

    @Test
    void badDateYieldsRowErrorButOtherRowsParse() {
        List<LegacyRow> rows = LegacyFileParser.parse("a.xls", html(
                tr("2026-11-15", "A1", "X", "1", "1", "1"),
                tr("16/11/2026", "A2", "Y", "1", "1", "1")));

        assertThat(rows.get(0).error()).contains("date");
        assertThat(rows.get(1).error()).isNull();
    }

    @Test
    void blankConsignmentYieldsRowError() {
        List<LegacyRow> rows = LegacyFileParser.parse("a.xls", html(tr("15/11/2026", "  ", "X", "1", "1", "1")));

        assertThat(rows.get(0).error()).contains("Consignment");
    }

    @Test
    void nonNumericWeightYieldsRowError() {
        List<LegacyRow> rows = LegacyFileParser.parse("a.xls", html(tr("15/11/2026", "A1", "X", "1", "abc", "1")));

        assertThat(rows.get(0).error()).contains("weight");
    }

    @Test
    void zeroPackagesBecomesOne() {
        List<LegacyRow> rows = LegacyFileParser.parse("a.xls", html(tr("15/11/2026", "A1", "X", "0", "1", "1")));

        assertThat(rows.get(0).error()).isNull();
        assertThat(rows.get(0).packages()).isEqualTo(1);
    }

    @Test
    void trimsAndUnescapesText() {
        List<LegacyRow> rows = LegacyFileParser.parse("a.xls",
                html(tr("15/11/2026", " A1 ", "RATNAMALA &amp; SONS ", "1", "1", "1")));

        assertThat(rows.get(0).consignmentNo()).isEqualTo("A1");
        assertThat(rows.get(0).consignee()).isEqualTo("RATNAMALA & SONS");
    }

    @Test
    void consignmentNumberLongerThan30IsRowError() {
        List<LegacyRow> rows = LegacyFileParser.parse("a.xls",
                html(tr("15/11/2026", "X".repeat(31), "X", "1", "1", "1")));

        assertThat(rows.get(0).error()).contains("30");
    }

    @Test
    void missingHeaderRejectsWholeFile() {
        byte[] bad = "<table><tr><th>Date</th><th>Foo</th></tr></table>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> LegacyFileParser.parse("a.xls", bad))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Missing column");
    }

    @Test
    void emptyFileRejected() {
        assertThatThrownBy(() -> LegacyFileParser.parse("a.xls", new byte[0]))
                .isInstanceOf(BusinessRuleException.class);
    }
}
