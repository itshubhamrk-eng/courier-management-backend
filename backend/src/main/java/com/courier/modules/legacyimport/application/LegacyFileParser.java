package com.courier.modules.legacyimport.application;

import com.courier.shared.exception.BusinessRuleException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the old software's consignment export. That tool writes an HTML table and names it
 * {@code .xls}, so the format is sniffed from the bytes, not the file name: anything that
 * starts with {@code <} is read as an HTML table, everything else goes to POI (real
 * {@code .xls} / {@code .xlsx}). Pure — no Spring, no database.
 *
 * <p>A bad <i>row</i> becomes a {@link LegacyRow} with {@code error} set; a bad <i>file</i>
 * (empty, too big, a required header missing) throws {@link BusinessRuleException}.
 */
public final class LegacyFileParser {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_ROWS = 20_000;
    static final int MAX_CONSIGNMENT_LENGTH = 30;

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern TR = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL = Pattern.compile("<t[dh]\\b[^>]*>(.*?)</t[dh]>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final List<String> REQUIRED = List.of("date", "consignment no", "consignor", "consignee",
            "from", "to", "package", "actual weight", "total");

    private LegacyFileParser() {
    }

    public static List<LegacyRow> parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleException("The uploaded file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessRuleException("The uploaded file is larger than 10 MB.");
        }
        List<List<String>> table = looksLikeHtml(bytes) ? readHtml(bytes) : readWorkbook(bytes);
        if (table.isEmpty()) {
            throw new BusinessRuleException("The uploaded file has no rows.");
        }
        Map<String, Integer> col = headerIndex(table.get(0));
        if (table.size() - 1 > MAX_ROWS) {
            throw new BusinessRuleException("The file has more than %d rows.".formatted(MAX_ROWS));
        }
        List<LegacyRow> out = new ArrayList<>();
        int rowNo = 0;
        for (List<String> cells : table.subList(1, table.size())) {
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            out.add(toRow(++rowNo, cells, col));
        }
        return out;
    }

    // ------------------------------------------------------------------ row mapping

    private static LegacyRow toRow(int rowNo, List<String> cells, Map<String, Integer> col) {
        String no = cell(cells, col, "consignment no");
        String dateText = cell(cells, col, "date");
        String weightText = cell(cells, col, "actual weight");
        String totalText = cell(cells, col, "total");
        String packageText = cell(cells, col, "package");
        List<String> errors = new ArrayList<>();

        if (no.isEmpty()) {
            errors.add("Consignment no is missing");
        } else if (no.length() > MAX_CONSIGNMENT_LENGTH) {
            errors.add("Consignment no is longer than %d characters".formatted(MAX_CONSIGNMENT_LENGTH));
        }
        LocalDate date = null;
        try {
            date = LocalDate.parse(dateText, DATE);
        } catch (DateTimeException e) {
            errors.add("Invalid date '%s' (expected dd/MM/yyyy)".formatted(dateText));
        }
        BigDecimal weight = decimal(weightText, "weight", errors);
        BigDecimal total = decimal(totalText, "total", errors);
        int packages = 1;
        if (!packageText.isEmpty()) {
            try {
                packages = Math.max(1, new BigDecimal(packageText.replace(",", "")).intValue());
            } catch (NumberFormatException e) {
                errors.add("Invalid package count '%s'".formatted(packageText));
            }
        }
        return new LegacyRow(rowNo, no, date, cell(cells, col, "consignor"), cell(cells, col, "consignee"),
                cell(cells, col, "from"), cell(cells, col, "to"), packages, weight, total,
                errors.isEmpty() ? null : String.join("; ", errors));
    }

    /** Blank counts as zero (the old tool exports 0.00 for unpriced rows); text or negative is an error. */
    private static BigDecimal decimal(String text, String label, List<String> errors) {
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = new BigDecimal(text.replace(",", ""));
            if (value.signum() < 0) {
                errors.add("Invalid %s '%s' (negative)".formatted(label, text));
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add("Invalid %s '%s'".formatted(label, text));
            return null;
        }
    }

    private static String cell(List<String> cells, Map<String, Integer> col, String header) {
        int i = col.get(header);
        return i < cells.size() ? cells.get(i).trim() : "";
    }

    private static Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            index.putIfAbsent(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        for (String required : REQUIRED) {
            if (!index.containsKey(required)) {
                throw new BusinessRuleException("Missing column: " + required);
            }
        }
        return index;
    }

    // ------------------------------------------------------------------ HTML table

    private static boolean looksLikeHtml(byte[] bytes) {
        int i = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < bytes.length && (bytes[i] == ' ' || bytes[i] == '\t' || bytes[i] == '\r' || bytes[i] == '\n')) {
            i++;
        }
        return i < bytes.length && bytes[i] == '<';
    }

    private static List<List<String>> readHtml(byte[] bytes) {
        String html = new String(bytes, StandardCharsets.UTF_8);
        List<List<String>> table = new ArrayList<>();
        Matcher rows = TR.matcher(html);
        while (rows.find()) {
            List<String> cells = new ArrayList<>();
            Matcher c = CELL.matcher(rows.group(1));
            while (c.find()) {
                cells.add(unescape(TAG.matcher(c.group(1)).replaceAll("")).trim());
            }
            table.add(cells);
        }
        return table;
    }

    private static String unescape(String s) {
        return s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    // ------------------------------------------------------------------ real workbook

    private static List<List<String>> readWorkbook(byte[] bytes) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        List<List<String>> table = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                int last = Math.max(row.getLastCellNum(), 0);
                List<String> cells = new ArrayList<>(last);
                for (int i = 0; i < last; i++) {
                    Cell cell = row.getCell(i);
                    if (cell == null) {
                        cells.add("");
                    } else if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        cells.add(DATE_OUT.format(cell.getLocalDateTimeCellValue().toLocalDate()));
                    } else {
                        cells.add(formatter.formatCellValue(cell).trim());
                    }
                }
                table.add(cells);
            }
        } catch (IOException | RuntimeException e) {
            throw new BusinessRuleException("The file could not be read as an Excel or HTML-table export.");
        }
        return table;
    }

    private static final DateTimeFormatter DATE_OUT = DateTimeFormatter.ofPattern("dd/MM/uuuu");
}
